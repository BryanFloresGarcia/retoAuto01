package com.casino.qa.utils;

import com.casino.qa.config.TestConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Grabador de video del escenario por <em>polling</em> del Chrome DevTools Protocol.
 *
 * <p><b>Por que polling y no {@code Page.startScreencast}.</b> El streaming real del screencast
 * exige recibir eventos del bus CDP. {@code executeCdpCommand} solo ejecuta el comando y no
 * entrega eventos, por lo que el streaming requeriria agregar el artifact
 * {@code selenium-devtools-vNNN} al pom. Con polling se obtiene video sin dependencias nuevas:
 * se pide un fotograma cada N milisegundos y ffmpeg concatena la secuencia.</p>
 *
 * <p><b>Limitaciones conocidas, asumidas de forma explicita:</b></p>
 * <ul>
 *   <li>El video no es tiempo real exacto: durante esperas largas hay fotogramas identicos.</li>
 *   <li>{@link WebDriver} no es thread-safe y aqui se usa desde un hilo distinto al del test.
 *       Se serializa el acceso al driver con un lock, pero la mitigacion real para ejecutarse
 *       en paralelo es desactivar el video (por eso {@code @ParallelAndCucumber} lo desactiva).</li>
 *   <li>Solo funciona en navegadores Chromium (Chrome/Edge). Para Firefox se omite con warning.</li>
 * </ul>
 *
 * <p><b>ffmpeg es obligatorio</b> (decision de proyecto): si no esta en el PATH, {@link #start}
 * falla con un mensaje accionable en vez de degradar silenciosamente.</p>
 */
public class ScreenRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenRecorder.class);
    private static final long FFMPEG_TIMEOUT_SECONDS = 120;

    private final TestConfig config;
    private final ReentrantLock driverLock = new ReentrantLock();
    private final AtomicInteger frameIndex = new AtomicInteger();

    private volatile boolean running;
    private Thread worker;
    private WebDriver driver;
    private Path framesDir;
    private Path videoFile;
    private boolean ffmpegAvailable;

    public ScreenRecorder(TestConfig config) {
        this.config = config;
    }

    /** Solo Chromium expone el dominio {@code Page} del CDP. */
    public static boolean supports(WebDriver driver) {
        return driver instanceof ChromeDriver || driver instanceof EdgeDriver;
    }

    public boolean isActive() {
        return running;
    }

    public Path videoFile() {
        return videoFile;
    }

    /**
     * Arranca la captura. Falla si ffmpeg no esta disponible.
     *
     * @param driver driver del hilo del escenario
     * @param slug   nombre seguro del escenario, usado para carpeta y archivo
     */
    public void start(WebDriver driver, String slug) {
        this.driver = driver;
        this.ffmpegAvailable = ffmpegInstalled(config.evidence().ffmpegPath());

        if (!ffmpegAvailable) {
            throw new IllegalStateException(String.format(
                    "ffmpeg es obligatorio para generar la evidencia en video y no se encontro "
                            + "el ejecutable '%s' en el PATH. Instalalo con 'winget install Gyan.FFmpeg' "
                            + "o desactiva la grabacion con qa.evidence.video-enabled=false.",
                    config.evidence().ffmpegPath()));
        }

        if (!supports(driver)) {
            LOG.warn("[VIDEO] {} no soporta CDP; se omite la grabacion de video",
                    driver.getClass().getSimpleName());
            return;
        }

        try {
            framesDir = config.evidence().directory().resolve(slug).resolve("frames");
            Files.createDirectories(framesDir);
            String format = normalizeFormat(config.evidence().videoFormat());
            videoFile = config.evidence().directory().resolve(slug + "." + format);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo preparar la carpeta de frames: " + e.getMessage(), e);
        }

        running = true;
        worker = new Thread(this::captureLoop, "screen-recorder-" + slug);
        worker.setDaemon(true);
        worker.start();
        LOG.info("[VIDEO] grabacion iniciada -> {}", videoFile);
    }

    /** Detiene la captura, codifica los frames y devuelve el video (o {@code null}). */
    public Path stop() {
        running = false;
        if (worker != null) {
            try {
                worker.join(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
        if (videoFile == null) {
            return null;
        }
        try {
            Path encoded = encode();
            deleteFrames();
            LOG.info("[VIDEO] grabacion finalizada -> {}", encoded);
            return encoded;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("[VIDEO] no se pudo codificar el video; se conservan los frames en {}: {}",
                    framesDir, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------- captura

    private void captureLoop() {
        long intervalMillis = Math.max(50, config.evidence().videoFrameInterval().toMillis());
        while (running) {
            try {
                byte[] frame = captureFrame();
                if (frame != null && frame.length > 0) {
                    writeFrame(frame);
                }
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                LOG.warn("[VIDEO] no se pudo escribir el fotograma: {}", e.getMessage());
            } catch (RuntimeException e) {
                LOG.debug("[VIDEO] captura fallida, se reintenta: {}", e.getMessage());
            }
        }
    }

    private byte[] captureFrame() {
        driverLock.lock();
        try {
            Object result = cdp().executeCdpCommand("Page.captureScreenshot",
                    Map.of("format", "jpeg", "quality", 80));
            if (result instanceof Map<?, ?> map && map.get("data") instanceof String base64) {
                return Base64.getDecoder().decode(base64);
            }
            return null;
        } finally {
            driverLock.unlock();
        }
    }

    private org.openqa.selenium.chromium.ChromiumDriver cdp() {
        return (org.openqa.selenium.chromium.ChromiumDriver) driver;
    }

    private void writeFrame(byte[] frame) throws IOException {
        Path file = framesDir.resolve(String.format("%04d.jpg", frameIndex.getAndIncrement()));
        Files.write(file, frame);
    }

    // ----------------------------------------------------------------- codificacion

    private Path encode() throws IOException, InterruptedException {
        int fps = Math.max(1, config.evidence().videoFps());
        List<String> command = ffmpegCommand(fps);

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ffmpeg excedio " + FFMPEG_TIMEOUT_SECONDS + " s");
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg termino con codigo " + process.exitValue() + ": " + output.strip());
        }
        if (!Files.exists(videoFile)) {
            throw new IOException("ffmpeg no produjo el archivo esperado: " + videoFile);
        }
        return videoFile;
    }

    private List<String> ffmpegCommand(int fps) {
        String format = normalizeFormat(config.evidence().videoFormat());
        List<String> command = new java.util.ArrayList<>(List.of(
                config.evidence().ffmpegPath(),
                "-y", "-hide_banner", "-loglevel", "error",
                "-framerate", String.valueOf(fps),
                "-i", framesDir.resolve("%04d.jpg").toString()));

        if ("webm".equals(format)) {
            command.addAll(List.of("-c:v", "libvpx-vp9", "-b:v", "1M"));
        } else {
            command.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-crf", "28"));
        }
        command.addAll(List.of("-pix_fmt", "yuv420p", "-r", String.valueOf(fps), videoFile.toString()));
        return command;
    }

    private void deleteFrames() {
        if (framesDir == null || !Files.exists(framesDir)) {
            return;
        }
        try (var paths = Files.walk(framesDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOG.debug("[VIDEO] no se pudo borrar {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            LOG.debug("[VIDEO] no se pudo limpiar la carpeta de frames: {}", e.getMessage());
        }
    }

    private static String normalizeFormat(String format) {
        return "webm".equalsIgnoreCase(format) ? "webm" : "mp4";
    }

    private static boolean ffmpegInstalled(String executable) {
        try {
            Process process = new ProcessBuilder(executable, "-version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            return process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}