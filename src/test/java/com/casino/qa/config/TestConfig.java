package com.casino.qa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuracion tipada del framework (prefijo {@code qa}).
 *
 * <p>Todo lo parametrizable vive aqui: URL, navegador, timeouts, credenciales de prueba, reglas
 * no funcionales del modulo de Deposito "Pago con QR" y politicas de evidencia. Ninguna pagina,
 * ningun Step ni ningun Step Definition debe contener valores "hardcodeados" de negocio ni de
 * infraestructura.</p>
 */
@ConfigurationProperties(prefix = "qa")
public record TestConfig(
        String baseUrl,
        String baseUrlSecure,
        Browser browser,
        Timeouts timeouts,
        Credentials user,
        Qr qr,
        Evidence evidence
) {

    /** Configuracion del navegador. */
    public record Browser(String type, boolean headless, String windowSize, Integer pageLoadTimeoutSec) {
    }

    /** Timeouts normalizados por criticidad (evita TimeUnit hardcodeado). */
    public record Timeouts(Duration implicit, Duration explicit, Duration shortWait, Duration poll) {
    }

    /** Credenciales de la cuenta de pruebas del casino. */
    public record Credentials(String username, String password) {
    }

    /** Reglas no funcionales exigidas al servicio de generacion de QR. */
    public record Qr(Duration maxGenerationTime, Duration validity, Duration pollInterval) {
    }

    /**
     * Politica de evidencia: capturas PNG y grabacion de video.
     *
     * @param directory          carpeta destino de capturas y videos
     * @param screenshotOnFailure captura automatica cuando el escenario falla
     * @param screenshotPerStep  captura tras cada paso (muy costoso, solo depuracion)
     * @param videoEnabled       activa la grabacion de la sesion
     * @param videoRequired      si {@code true}, la ausencia de ffmpeg hace fallar el escenario;
     *                           si {@code false}, se degrada a solo capturas PNG
     * @param videoFps           fotogramas por segundo del video resultante
     * @param videoFrameInterval intervalo de captura por polling
     * @param ffmpegPath         ejecutable de ffmpeg usado para codificar los frames
     * @param videoFormat        contenedor de salida ({@code mp4} o {@code webm})
     */
    public record Evidence(
            Path directory,
            boolean screenshotOnFailure,
            boolean screenshotPerStep,
            boolean videoEnabled,
            boolean videoRequired,
            int videoFps,
            Duration videoFrameInterval,
            String ffmpegPath,
            String videoFormat
    ) {
    }
}