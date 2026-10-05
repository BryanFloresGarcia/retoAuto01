package com.casino.qa.stepDefinitions;

import com.casino.qa.steps.QrPaymentSteps;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Step Definitions de la pasarela "Pago con QR".
 *
 * <p>Unica responsabilidad: enlazar el texto Gherkin con un metodo de {@link QrPaymentSteps}.
 * No hay logica de negocio ni aserciones aqui.</p>
 */
public class QrPaymentStepDefinitions {

    @Autowired
    private QrPaymentSteps qrPaymentSteps;

    @Dado("que he abierto la pasarela de Pago con QR con monto {string} y moneda {string}")
    public void queHeAbiertoLaPasarelaQr(String monto, String moneda) {
        qrPaymentSteps.abrirPasarelaQr(monto, moneda);
    }

    @Entonces("se genera un codigo QR de pago valido")
    public void seGeneraUnCodigoQrDePagoValido() {
        qrPaymentSteps.seGeneraQrValido();
    }

    @Y("el QR muestra una referencia unica")
    public void elQrMuestraUnaReferenciaUnica() {
        qrPaymentSteps.elQrMuestraReferenciaUnica();
    }

    @Y("el estado del QR es {string}")
    public void elEstadoDelQrEs(String estado) {
        qrPaymentSteps.estadoQrEs(estado);
    }

    @Cuando("simulo la confirmacion de pago desde la pasarela")
    public void simuloLaConfirmacionDePagoDesdeLaPasarela() {
        qrPaymentSteps.simularConfirmacionPago();
    }

    @Entonces("el deposito se registra como {string} en el sistema")
    public void elDepositoSeRegistraComo(String estado) {
        qrPaymentSteps.depositoRegistradoComo(estado);
    }

    @Y("puedo visualizarlo en el historial de depositos")
    public void puedoVisualizarloEnElHistorialDeDepositos() {
        qrPaymentSteps.puedoVisualizarloEnElHistorial();
    }

    @Entonces("el sistema muestra un mensaje de error o timeout controlado")
    public void elSistemaMuestraErrorOTimeoutControlado() {
        qrPaymentSteps.sistemaMuestraErrorOTimeoutControlado();
    }
}