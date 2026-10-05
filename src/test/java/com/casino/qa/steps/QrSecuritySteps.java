package com.casino.qa.steps;

import com.casino.qa.pages.DepositPage;
import com.casino.qa.pages.QrDepositPage;
import com.casino.qa.utils.ScenarioState;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Steps del camino de seguridad: un codigo QR ya emitido no debe poder reutilizarse para
 * iniciar otro deposito.
 */
@Component
public class QrSecuritySteps {

    private final ObjectProvider<DepositPage> depositPage;
    private final ObjectProvider<QrDepositPage> qrDepositPage;
    private final QrPaymentSteps qrPaymentSteps;
    private final ScenarioState state;

    public QrSecuritySteps(ObjectProvider<DepositPage> depositPage,
                           ObjectProvider<QrDepositPage> qrDepositPage,
                           QrPaymentSteps qrPaymentSteps,
                           ScenarioState state) {
        this.depositPage = depositPage;
        this.qrDepositPage = qrDepositPage;
        this.qrPaymentSteps = qrPaymentSteps;
        this.state = state;
    }

    /** Genera un QR valido y conserva su huella como referencia del caso. */
    public void heGeneradoQrExitosamente() {
        qrPaymentSteps.abrirPasarelaQr("50.00", "PEN");
        qrPaymentSteps.recordarHuellaDelQr();
    }

    /** Cierra el modal, reabre el deposito e invoca de nuevo la pasarela con el mismo QR. */
    public void intentoReutilizarElQr() {
        qrDepositPage.getObject().closeModal();
        DepositPage deposit = depositPage.getObject();
        deposit.openFromHeader();
        deposit.selectQrPaymentMethod();
        qrDepositPage.getObject().reopenQr();
    }

    public void elSistemaImpideLaReutilizacion() {
        Assertions.assertThat(qrDepositPage.getObject().isQrMarkedAsUsed())
                .as("El QR '%s' no debe poder reutilizarse para un nuevo deposito", state.qrFingerprint())
                .isTrue();
    }
}