package com.casino.qa.steps;

import com.casino.qa.config.TestConfig;
import com.casino.qa.pages.HomePage;
import com.casino.qa.pages.LoginPage;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Steps de autenticacion. Es la precondicion de todos los escenarios del reto.
 *
 * <p>Contiene las validaciones (AssertJ) y orquesta paginas. No contiene localizadores ni
 * esperas tecnicas.</p>
 *
 * <p>Es un bean de Spring porque <strong>no</strong> es glue de Cucumber: las anotaciones
 * {@code @Dado/@Cuando/@Entonces} viven en {@code stepDefinitions}.</p>
 *
 * <p><b>Por que {@link ObjectProvider} y no inyeccion directa.</b> Las paginas son beans
 * {@code prototype} enlazados al driver del hilo. Este bean es singleton, asi que una inyeccion
 * directa resolveria una unica instancia de cada pagina en el arranque del contexto y esa misma
 * instancia se reutilizaria en todos los escenarios: sus proxies {@code @FindBy} quedarian
 * apuntando al driver del primer escenario. Resolviendo en cada llamada, cada escenario recibe
 * paginas nuevas y correctamente enlazadas a su driver.</p>
 */
@Component
public class LoginSteps {

    private final ObjectProvider<HomePage> homePage;
    private final ObjectProvider<LoginPage> loginPage;
    private final TestConfig config;

    public LoginSteps(ObjectProvider<HomePage> homePage,
                      ObjectProvider<LoginPage> loginPage,
                      TestConfig config) {
        this.homePage = homePage;
        this.loginPage = loginPage;
        this.config = config;
    }

    /** Abre el portal, completa el login con la cuenta de pruebas y valida el exito. */
    public void autenticarConUsuarioDePruebas() {
        HomePage home = homePage.getObject();
        LoginPage login = loginPage.getObject();

        home.open()
                .openLoginModal();
        login.loginAs(config.user().username(), config.user().password());

        boolean loggedIn = login.isLoggedIn(config.user().username());
        // El portal puede rechazar por credenciales o por bloqueo por intentos fallidos; sin este
        // detalle el fallo seria un booleano falso sin ninguna pista de la causa real.
        String portalMessage = login.errorMessage().filter(text -> !text.isBlank()).orElse("");
        Assertions.assertThat(loggedIn)
                .as("La cabecera debe mostrar la sesion iniciada tras el login con '%s'%s",
                        config.user().username(),
                        portalMessage.isEmpty() ? "" : ". Mensaje del portal: " + portalMessage)
                .isTrue();
        Assertions.assertThat(home.isDepositEntryVisible())
                .as("El boton DEPOSITAR debe estar visible tras iniciar sesion")
                .isTrue();
    }
}