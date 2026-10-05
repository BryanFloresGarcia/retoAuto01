package com.casino.qa.stepDefinitions;

import com.casino.qa.steps.DepositSteps;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Y;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Step Definitions del modulo de Deposito.
 *
 * <p>Unica responsabilidad: enlazar el texto Gherkin con un metodo de {@link DepositSteps}.
 * No hay logica de negocio ni aserciones aqui.</p>
 */
public class DepositStepDefinitions {

    @Autowired
    private DepositSteps depositSteps;

    @Dado("que estoy autenticado y en el modulo de deposito")
    public void queEstoyAutenticadoYEnElModuloDeposito() {
        depositSteps.estarAutenticadoEnModuloDeposito();
    }

    @Cuando("abro el modulo de Deposito desde el header")
    public void abroElModuloDepositoDesdeElHeader() {
        depositSteps.abrirModuloDepositoDesdeHeader();
    }

    @Y("selecciono la pasarela de pago {string}")
    public void seleccionoLaPasarelaDePago(String pasarela) {
        depositSteps.seleccionarPasarela(pasarela);
    }

    @Y("completo el monto {string} y la moneda {string}")
    public void completoElMontoYLaMoneda(String monto, String moneda) {
        depositSteps.completarMontoYMoneda(monto, moneda);
    }

    @Y("envio la solicitud de deposito")
    public void envioLaSolicitudDeposito() {
        depositSteps.enviarSolicitudDeposito();
    }

    @Cuando("cambio el monto a {string}")
    public void cambioElMontoA(String monto) {
        depositSteps.cambiarMontoA(monto);
    }

    @Entonces("el sistema acepta el monto dentro del rango permitido")
    public void elSistemaAceptaElMontoEnElRangoPermitido() {
        depositSteps.sistemaAceptaElMontoEnRango();
    }

    @Entonces("el sistema muestra un mensaje de error de monto invalido")
    public void elSistemaMuestraErrorDeMontoInvalido() {
        depositSteps.sistemaMuestraErrorDeMontoInvalido();
    }
}