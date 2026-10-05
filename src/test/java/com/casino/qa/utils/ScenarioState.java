package com.casino.qa.utils;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Estado compartido entre pasos de un mismo escenario.
 *
 * <p><b>Por que existe.</b> Las paginas son beans {@code prototype}: cada punto de inyeccion
 * recibe una instancia distinta. Si {@code QrDepositPage} guardara el instante de envio en un
 * campo propio, el paso que marca el envio y el paso que mide el tiempo de generacion
 * operates sobre objetos diferentes y la metrica seria siempre cero. Un bean singleton con
 * almacenamiento por {@link ThreadLocal} resuelve eso: todos los pasos del escenario comparten
 * estado, y dos escenarios en paralelo no se pisan porque corren en hilos distintos.</p>
 *
 * <p>El estado se limpia en {@code @After} por los hooks.</p>
 */
@Component
public class ScenarioState {

    private static final String QR_SUBMISSION = "qr.submissionInstant";
    private static final String QR_GENERATION_TIME = "qr.generationTime";
    private static final String QR_FINGERPRINT = "qr.fingerprint";

    private final ThreadLocal<java.util.Map<String, Object>> store =
            ThreadLocal.withInitial(java.util.LinkedHashMap::new);

    // ------------------------------------------------------- medicion de generacion QR

    /** Instante en que el usuario confirma el deposito: arranque de la metrica "&lt; 3 s". */
    public void markQrSubmission() {
        store.get().put(QR_SUBMISSION, Instant.now());
        store.get().remove(QR_GENERATION_TIME);
    }

    public Instant qrSubmissionInstant() {
        return (Instant) store.get().get(QR_SUBMISSION);
    }

    /**
     * Tiempo de generacion ya medido, o {@code null} si todavia no se midio. Se cachea para que
     * varios pasos del mismo escenario affirmen sobre la misma medicion en lugar de acumular el
     * tiempo de cada invocacion.
     */
    public Duration qrGenerationTime() {
        return (Duration) store.get().get(QR_GENERATION_TIME);
    }

    public void cacheQrGenerationTime(Duration duration) {
        store.get().put(QR_GENERATION_TIME, duration);
    }

    // ------------------------------------------------------------- huella del QR

    /** Huella del ultimo QR generado, para el escenario de seguridad. */
    public void qrFingerprint(String fingerprint) {
        store.get().put(QR_FINGERPRINT, fingerprint);
    }

    public String qrFingerprint() {
        return (String) store.get().get(QR_FINGERPRINT);
    }

    /** Limpia el estado del hilo. Lo invoca el hook {@code @After}. */
    public void reset() {
        store.remove();
    }
}