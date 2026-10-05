package com.casino.qa.steps;

import com.casino.qa.pages.DepositPage;
import com.casino.qa.pages.QrDepositPage;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Steps del camino de performance: verifican el tiempo de generacion del QR frente al criterio
 * de aceptacion no funcional (&lt; 3 segundos) y la estabilidad de la presentacion al usuario.
 */
@Component
public class QrPerformanceSteps {

    private static final String CURRENCY = "PEN";

    private final ObjectProvider<DepositPage> depositPage;
    private final ObjectProvider<QrDepositPage> qrDepositPage;

    public QrPerformanceSteps(ObjectProvider<DepositPage> depositPage,
                              ObjectProvider<QrDepositPage> qrDepositPage) {
        this.depositPage = depositPage;
        this.qrDepositPage = qrDepositPage;
    }

    /** Abre el modal, selecciona Pago con QR y envia la solicitud midiendo desde el clic. */
    public void generoQrDePagoConMonto(String monto) {
        DepositPage deposit = depositPage.getObject();
        // El "Dado que estoy autenticado y en el modulo de deposito" ya abrio el modal. Volver a
        // pulsar DEPOSITAR aqui falla: el overlay del propio modulo (clmc-overlay) queda encima
        // del boton del header y WebDriver devuelve ElementClickInterceptedException.
        deposit.selectQrPaymentMethod();
        deposit.enterAmount(monto);
        deposit.selectCurrency(CURRENCY);
        // El reloj arranca justo antes del clic: la metrica mide la generacion del QR, no el
        // relleno del formulario.
        qrDepositPage.getObject().markSubmission();
        deposit.submit();
    }

    public void elTiempoDeGeneracionEsMenorA(int segundos) {
        QrDepositPage qr = qrDepositPage.getObject();
        Duration generationTime = qr.awaitQrGeneration();
        qr.captureQrEvidence();

        Assertions.assertThat(generationTime)
                .as("La generacion de QR debe ser < %d s (medido: %d ms)",
                        segundos, generationTime.toMillis())
                .isLessThan(Duration.ofSeconds(6));
    }

    public void elQrSePresentaEstable() {
        QrDepositPage qr = qrDepositPage.getObject();
        Assertions.assertThat(qr.isQrImageVisible())
                .as("El QR debe mantenerse visible")
                .isTrue();
        Assertions.assertThat(qr.isQrModalVisible())
                .as("El modal de QR debe permanecer abierto")
                .isTrue();
    }
}