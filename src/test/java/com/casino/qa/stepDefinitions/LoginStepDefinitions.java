package com.casino.qa.stepDefinitions;

import com.casino.qa.steps.LoginSteps;
import io.cucumber.java.es.Dado;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Step Definitions del flujo de autenticacion.
 *
 * <p>Unica responsabilidad: enlazar el texto Gherkin con un metodo de {@link LoginSteps}.
 * No hay logica de negocio ni aserciones aqui; ambos viven en la capa {@code steps}.</p>
 */
public class LoginStepDefinitions {

    @Autowired
    private LoginSteps loginSteps;

    @Dado("que me autentico con el usuario de pruebas en el casino online")
    public void queMeAutenticoConElUsuarioDePruebas() {
        loginSteps.autenticarConUsuarioDePruebas();
    }
}