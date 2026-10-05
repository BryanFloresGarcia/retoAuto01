package com.casino.qa.stepDefinitions;

import com.casino.qa.steps.QrPerformanceSteps;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Step Definitions del camino de performance.
 *
 * <p>Unica responsabilidad: enlazar el texto Gherkin con un metodo de {@link QrPerformanceSteps}.
 * No hay logica de negocio ni aserciones aqui.</p>
 */
public class QrPerformanceStepDefinitions {

    @Autowired
    private QrPerformanceSteps qrPerformanceSteps;

    @Cuando("genero un QR de pago con monto {string}")
    public void generoUnQrDePagoConMonto(String monto) {
        qrPerformanceSteps.generoQrDePagoConMonto(monto);
    }

    @Entonces("el tiempo de generacion del QR es menor a {int} segundos")
    public void elTiempoDeGeneracionDelQrEsMenorA(int segundos) {
        qrPerformanceSteps.elTiempoDeGeneracionEsMenorA(segundos);
    }

    @Y("el QR se presenta de forma estable al usuario")
    public void elQrSePresentaDeFormaEstable() {
        qrPerformanceSteps.elQrSePresentaEstable();
    }
}