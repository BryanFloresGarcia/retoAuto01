package com.casino.qa.stepDefinitions;

import com.casino.qa.steps.QrSecuritySteps;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Cuando;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Step Definitions del camino de seguridad (reutilizacion de QR).
 *
 * <p>Unica responsabilidad: enlazar el texto Gherkin con un metodo de {@link QrSecuritySteps}.
 * No hay logica de negocio ni aserciones aqui.</p>
 */
public class QrSecurityStepDefinitions {

    @Autowired
    private QrSecuritySteps qrSecuritySteps;

    @Dado("que he generado un QR de pago exitosamente")
    public void queHeGeneradoUnQrDePagoExitosamente() {
        qrSecuritySteps.heGeneradoQrExitosamente();
    }

    @Cuando("intento reutilizar el mismo codigo QR para un nuevo deposito")
    public void intentoReutilizarElMismoCodigoQr() {
        qrSecuritySteps.intentoReutilizarElQr();
    }

    @Entonces("el sistema impide la reutilizacion del QR y muestra una alerta")
    public void elSistemaImpideLaReutilizacionDelQr() {
        qrSecuritySteps.elSistemaImpideLaReutilizacion();
    }
}