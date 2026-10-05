package com.casino.qa.utils;

import com.casino.qa.config.TestConfig;
import io.cucumber.java.Scenario;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Destino de la evidencia del escenario en curso.
 *
 * <p>Concentra dos responsabilidades que antes estaban repartidas y fallaban de forma silenciosa:</p>
 * <ol>
 *   <li><b>Adjuntar</b> el artefacto al reporte HTML de Cucumber.</li>
 *   <li><b>Persistirlo en disco</b> dentro de {@code qa.evidence.directory}, que antes estaba
 *       configurado pero nunca se escribia.</li>
 * </ol>
 *
 * <p>El escenario se guarda en un {@link ThreadLocal} porque Cucumber ejecuta un escenario por
 * hilo y la suite puede correr en paralelo. Los hooks enlazan y liberan el escenario en
 * {@code @Before}/{@code @After}; si no hay ninguno enlazado la grabacion degrada a solo-disco,
 * de modo que las capturas nunca rompen un test.</p>
 */
@Component
public class ScenarioEvidenceRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(ScenarioEvidenceRecorder.class);
    private static final DateTimeFormatter TIME_STAMP = DateTimeFormatter.ofPattern("HHmmss-SSS");

    private final TestConfig config;
    private final ThreadLocal<Scenario> current = new ThreadLocal<>();

    public ScenarioEvidenceRecorder(TestConfig config) {
        this.config = config;
    }

    // ------------------------------------------------------------------ ciclo de vida

    public void bind(Scenario scenario) {
        current.set(scenario);
    }

    public void clear() {
        current.remove();
    }

    public Scenario scenario() {
        return current.get();
    }

    // -------------------------------------------------------------------- evidencia

    /**
     * Captura el navegador, adjunta el PNG al reporte y lo escribe en disco.
     *
     * @return ruta del PNG persistido, o {@code null} si el driver no soporta capturas
     */
    public Path capture(WebDriver driver, String label) {
        if (!(driver instanceof TakesScreenshot takesScreenshot)) {
            LOG.warn("[EVIDENCE] el driver {} no soporta capturas", driver.getClass().getSimpleName());
            return null;
        }
        String base64 = takesScreenshot.getScreenshotAs(OutputType.BASE64);
        byte[] bytes;
        try {
            bytes = takesScreenshot.getScreenshotAs(OutputType.BYTES);
        } catch (RuntimeException e) {
            LOG.warn("[EVIDENCE] no se pudo capturar la pantalla: {}", e.getMessage());
            return null;
        }

        Scenario scenario = current.get();
        if (scenario != null) {
            scenario.attach(base64, "image/png", label + "-" + stamp());
        }
        Path file = write(label + "-" + stamp() + ".png", bytes);
        LOG.info("[EVIDENCE] captura '{}' -> {}", label, file);
        return file;
    }

    /**
     * Vuelca el HTML de la pagina a disco.
     *
     * <p>Complementa a la captura: cuando un localizador no encuentra el elemento, la pregunta
     * util no es "que se ve" sino "que hay en el DOM". Con el markup se puede reconstruir el
     * selector correcto en lugar de adivinarlo. Nunca debe romper el test.</p>
     *
     * @return ruta del HTML persistido, o {@code null} si no se pudo obtener
     */
    public Path capturePageSource(WebDriver driver, String label) {
        String html;
        try {
            html = driver.getPageSource();
        } catch (RuntimeException e) {
            LOG.warn("[EVIDENCE] no se pudo leer el HTML de la pagina: {}", e.getMessage());
            return null;
        }
        return write(label + "-" + stamp() + ".html", html.getBytes(StandardCharsets.UTF_8));
    }

    /** Escribe un artefacto en {@code qa.evidence.directory} creando la carpeta si hace falta. */
    public Path write(String fileName, byte[] content) {
        try {
            Path directory = config.evidence().directory();
            Files.createDirectories(directory);
            Path file = directory.resolve(fileName);
            Files.write(file, content);
            return file;
        } catch (IOException | RuntimeException e) {
            LOG.warn("[EVIDENCE] no se pudo escribir {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    // --------------------------------------------------------------------- ayudantes

    /** Nombre de archivo seguro y unico a partir del nombre del escenario. */
    public static String slug(Scenario scenario) {
        String raw = scenario.getName() + "-" + Thread.currentThread().getId();
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        String base = normalized.isEmpty() ? "escenario" : normalized.toLowerCase();
        return base.length() > 80 ? base.substring(0, 80) : base;
    }

    private static String stamp() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + LocalTime.now().format(TIME_STAMP);
    }
}