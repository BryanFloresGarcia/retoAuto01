package com.casino.qa.steps;

import com.casino.qa.config.TestConfig;
import com.casino.qa.pages.DepositHistoryPage;
import com.casino.qa.pages.QrDepositPage;
import com.casino.qa.utils.ScenarioState;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Steps de la pasarela "Pago con QR": happy path, camino alternativo de confirmacion y el
 * exception path de timeout de la pasarela.
 *
 * <p>Las paginas se resuelven con {@link ObjectProvider} para que cada escenario reciba
 * instancias {@code prototype} enlazadas a su propio driver (ver {@link LoginSteps}).</p>
 */
@Component
public class QrPaymentSteps {

    private final ObjectProvider<QrDepositPage> qrDepositPage;
    private final ObjectProvider<DepositHistoryPage> depositHistoryPage;
    private final DepositSteps depositSteps;
    private final ScenarioState state;
    private final TestConfig config;

    public QrPaymentSteps(ObjectProvider<QrDepositPage> qrDepositPage,
                          ObjectProvider<DepositHistoryPage> depositHistoryPage,
                          DepositSteps depositSteps,
                          ScenarioState state,
                          TestConfig config) {
        this.qrDepositPage = qrDepositPage;
        this.depositHistoryPage = depositHistoryPage;
        this.depositSteps = depositSteps;
        this.state = state;
        this.config = config;
    }

    // ------------------------------------------------------------- precondiciones

    /** Autentica, abre el modal, selecciona Pago con QR, completa el monto y envia. */
    public void abrirPasarelaQr(String monto, String moneda) {
        depositSteps.abrirPasarelaQrYEnviar(monto, moneda);

        QrDepositPage qr = qrDepositPage.getObject();
        qr.awaitQrGeneration();
        Assertions.assertThat(qr.isQrModalVisible())
                .as("El modal de QR debe estar visible tras generar el QR")
                .isTrue();
        Assertions.assertThat(qr.isQrImageVisible())
                .as("La imagen del QR debe renderizarse")
                .isTrue();
    }

    // ------------------------------------------------------------------- acciones

    public void simularConfirmacionPago() {
        qrDepositPage.getObject().confirmPayment();
    }

    // --------------------------------------------------------------- validaciones

    /** Criterio no funcional del reto: el QR debe generarse en menos de 3 s. */
    public void seGeneraQrValido() {
        QrDepositPage qr = qrDepositPage.getObject();
        Duration generationTime = qr.awaitQrGeneration();
        qr.captureQrEvidence();

        Assertions.assertThat(generationTime)
                .as("El tiempo de generacion del QR debe ser < %s", config.qr().maxGenerationTime())
                .isLessThan(config.qr().maxGenerationTime());
        Assertions.assertThat(qr.isQrImageVisible())
                .as("La imagen del QR debe presentarse al usuario")
                .isTrue();
        Assertions.assertThat(qr.isQrPayloadWellFormed())
                .as("El payload del QR debe tener formato valido")
                .isTrue();
    }

/**
     * El QR debe identificar de forma unica la operacion de cobro.
     *
     * <p>Este portal no imprime codigo de operacion, asi que la referencia se deriva del propio QR
     * decodificado. Se comprueba que exista, que no este vacia y que no cambie entre lecturas: si el
     * QR se regenerara por debajo, o la pagina se re-renderizara con otro payload, la referencia
     * cambiaria y el test detectaria que se esta midiendo sobre una imagen obsoleta.</p>
     */
    public void elQrMuestraReferenciaUnica() {
        QrDepositPage qr = qrDepositPage.getObject();
        Optional<String> reference = qr.qrReference();
        Assertions.assertThat(reference)
                .as("La referencia del QR debe estar presente")
                .isPresent();
        String value = reference.orElseThrow();
        Assertions.assertThat(value)
                .as("La referencia no puede estar vacia")
                .isNotBlank();
        Assertions.assertThat(qr.qrReference())
                .as("La referencia debe ser estable entre lecturas del mismo QR")
                .contains(value);
        Assertions.assertThat(qr.qrPayload())
                .as("El payload del QR debe exponerse para trazabilidad")
                .isPresent();
    }

    public void estadoQrEs(String estado) {
        Assertions.assertThat(qrDepositPage.getObject().currentStatus().orElse(""))
                .as("El estado actual del QR debe ser '%s'", estado)
                .containsIgnoringCase(estado);
    }

    public void depositoRegistradoComo(String estado) {
        QrDepositPage qr = qrDepositPage.getObject();
        boolean reachedExpected = qr.waitForStatusSuccess(Duration.ofSeconds(30));
        Assertions.assertThat(reachedExpected
                        || qr.currentStatus().orElse("").contains(estado))
                .as("El estado final del deposito debe ser '%s'", estado)
                .isTrue();
    }

    public void puedoVisualizarloEnElHistorial() {
        DepositHistoryPage history = depositHistoryPage.getObject();
        history.open();
        Assertions.assertThat(history.hasRecordWithStatus("Exitoso"))
                .as("El historial de depositos debe contener un registro Exitoso")
                .isTrue();
    }

    /** Exception path: la pasarela debe degradar con un error o estado fallido controlado. */
    public void sistemaMuestraErrorOTimeoutControlado() {
        QrDepositPage qr = qrDepositPage.getObject();
        boolean hasError = qr.gatewayErrorMessage().isPresent()
                || qr.currentStatus().map(s -> s.toLowerCase().contains("fallido")).orElse(false);
        Assertions.assertThat(hasError)
                .as("La pasarela debe devolver un error o estado fallido ante timeout")
                .isTrue();
    }

    /** Recorda la huella del QR actual para el escenario de reutilizacion. */
    public void recordarHuellaDelQr() {
        state.qrFingerprint(qrDepositPage.getObject().qrFingerprint().orElse("desconocida"));
    }
}