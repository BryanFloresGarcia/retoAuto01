package com.casino.qa.pages;

import com.casino.qa.config.DriverProvider;
import com.casino.qa.utils.ScenarioEvidenceRecorder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Page Object del flujo de autenticacion (modal "Iniciar sesion" del header).
 *
 * <p>Cubre los caminos Happy, Negative y Exception del login, que son la precondicion de todo
 * el flujo de Deposito "Pago con QR". Solo interactua con el DOM: no decide si el login fue
 * exitoso, eso lo valida {@code LoginSteps}.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class LoginPage extends BasePage {

    // Tokens del design system, expandidos a constantes porque las anotaciones @FindBy solo
    // admiten expresiones constantes en tiempo de compilacion. El predicado de token completo
    // (espacios de guarda) evita que 'clmc-user' coincida con 'clmc-usuario'.
    private static final String TOK_INPUT =
            "contains(concat(' ',normalize-space(@class),' '),' clmc-input ')";
    private static final String TOK_ERROR =
            "contains(concat(' ',normalize-space(@class),' '),' clmc-error ')";
    private static final String TOK_ALERT =
            "contains(concat(' ',normalize-space(@class),' '),' clmc-alert ')";
    private static final String TOK_POINTER =
            "contains(concat(' ',normalize-space(@class),' '),' clmc-pointer ')";

    // Campos del formulario: se identifican por tipo/atributo/nombre, no por clase hasheada.
    @FindBy(xpath = "//input[contains(@type,'email') or contains(@name,'user') or contains(@id,'user') or contains(@placeholder,'usuario')]")
    private WebElement usernameField;
    @FindBy(xpath = "//input[@type='text' and (contains(@autocomplete,'username') or contains(@name,'correo') or contains(@id,'correo') or contains(@placeholder,'correo'))]")
    private WebElement usernameFieldAlt;
    @FindBy(xpath = "//input[contains(@name,'usuario') or contains(@id,'usuario')]")
    private WebElement usernameFieldAlt2;
    @FindBy(xpath = "//input[not(@type) or @type='text'][" + TOK_INPUT + "]")
    private WebElement usernameFieldAlt3;

    @FindBy(xpath = "//input[@type='password' or contains(@name,'password') or contains(@name,'clave') or contains(@id,'password')]")
    private WebElement passwordField;
    @FindBy(xpath = "//input[@type='password']")
    private WebElement passwordFieldAlt;
    @FindBy(xpath = "//input[contains(@placeholder,'contrasena') or contains(@placeholder,'password')]")
    private WebElement passwordFieldAlt2;

    // Envio del formulario. Todo se acota a SCOPE_DIALOG: el boton con texto "INGRESAR" tambien
    // existe en la cabecera y, al quedar debajo del dialogo, produce click interceptado.
    // El texto real llega como "INICIAR SESIA"N" (acentos rotos), asi que la clave es sin tilde.
    // Se excluyen los type="button": el primero del formulario es el toggle MOSTRAR/OCULTAR de la
    // contrasena, no el envio.
    @FindBy(xpath = SCOPE_DIALOG + "//form//button[.//*[contains(" + UPPER_XP + ",'INICIAR SES')]]")
    private WebElement loginSubmit;
    @FindBy(xpath = SCOPE_DIALOG + "//form//button[not(@type='button')]")
    private WebElement loginSubmitAlt;
    @FindBy(xpath = SCOPE_DIALOG + "//button[@type='submit']")
    private WebElement loginSubmitAlt2;
    @FindBy(xpath = SCOPE_DIALOG + "//input[@type='submit']")
    private WebElement loginSubmitAlt3;

// Mensaje de error del login. Se acota al dialogo porque el aviso de cookies tambien usa
    // role="alert" y, sin acotar, contaminaba el diagnostico del login.
    @FindBy(xpath = SCOPE_DIALOG + "//*[contains(@class,'MuiAlert-message')]")
    private WebElement loginError;
    @FindBy(xpath = SCOPE_DIALOG + "//*[@role='alert']")
    private WebElement loginErrorAlt;
    @FindBy(xpath = SCOPE_DIALOG + "//*[contains(" + UPPER_XP + ",'RESTABLECER') or contains(" + UPPER_XP + ",'MAXIMO DE INTENTOS') or contains(" + UPPER_XP + ",'CREDENCIAL') or contains(" + UPPER_XP + ",'INCORRECT')]")
    private WebElement loginErrorAlt2;

    // Contenedor del modal de login: es un dialog de Material UI.
    @FindBy(xpath = SCOPE_DIALOG)
    private WebElement loginModal;
    @FindBy(css = "#app-modal")
    private WebElement loginModalAlt;
    @FindBy(xpath = "//form[.//input[@type='password']]")
    private WebElement loginModalAlt2;

    // Cierre del modal.
    @FindBy(xpath = "//*[contains(@data-testid,'CloseIcon')]/ancestor::*[self::button or self::div or @role='button'][1]")
    private WebElement closeModalButton;
    @FindBy(xpath = "//button[contains(@class,'clmc-close') or contains(@aria-label,'Cerrar') or contains(@aria-label,'Close')]")
    private WebElement closeModalButtonAlt;
    @FindBy(xpath = "//*[@id='error400Popup']//*[contains(@class,'clmc-pointer')]")
    private WebElement closeModalButtonAlt2;

// Usuario autenticado. El portal no imprime el nombre de usuario en la cabecera: la senal de
    // sesion iniciada es que desaparezca el boton#login de anonimo y aparezca el boton DEPOSITAR.
    @FindBy(xpath = "//button[contains(" + UPPER_XP + ",'DEPOSITAR') or .//*[contains(" + UPPER_XP + ",'DEPOSITAR')]]")
    private WebElement currentUser;
    @FindBy(xpath = "//*[@id='main-header']//*[contains(@class,'avatar') or contains(@class,'Avatar') or contains(@id,'USER')]")
    private WebElement currentUserAlt;
    @FindBy(xpath = "//*[contains(" + UPPER_XP + ",'MI CUENTA') or contains(" + UPPER_XP + ",'SALIR')]")
    private WebElement currentUserAlt2;

    private static final By DEPOSIT_ENTRY_XP =
            By.xpath("//button[contains(" + UPPER_XP + ",'DEPOSITAR') or .//*[contains(" + UPPER_XP + ",'DEPOSITAR')]]");

    public LoginPage(DriverProvider driverProvider, ScenarioEvidenceRecorder evidence) {
        super(driverProvider, evidence);
    }

    // ------------------------------------------------------------------ acciones

    /** Escribe usuario y contrasena en el modal de login. */
    public LoginPage enterCredentials(String username, String password) {
        type(require(usernameField, usernameFieldAlt, usernameFieldAlt2, usernameFieldAlt3), username);
        type(require(passwordField, passwordFieldAlt, passwordFieldAlt2), password);
        return this;
    }

    /** Escribe un usuario inexistente y una contrasena arbitraria. */
    public LoginPage enterInvalidCredentials() {
        return enterCredentials("usuario_inexistente_" + System.currentTimeMillis(), "ClaveIncorrecta!1");
    }

    /** Escribe usuario valido con contrasena incorrecta. */
    public LoginPage enterValidUserWithWrongPassword(String username) {
        return enterCredentials(username, "ClaveIncorrecta!1");
    }

    public LoginPage submit() {
        clickAny(loginSubmit, loginSubmitAlt, loginSubmitAlt2);
        return this;
    }

    /** Completa y envia el formulario de login. */
    public LoginPage loginAs(String username, String password) {
        return enterCredentials(username, password).submit();
    }

    /** Cierra el modal de login sin autenticarse. */
    public LoginPage closeModal() {
        firstDisplayed(closeModalButton, closeModalButtonAlt, closeModalButtonAlt2)
                .ifPresent(WebElement::click);
        return this;
    }

    // ------------------------------------------------------------------ lecturas

    /** Mensaje de error visible del modal de login, si existe. */
    public Optional<String> errorMessage() {
        return firstDisplayed(loginError, loginErrorAlt, loginErrorAlt2)
                .map(WebElement::getText)
                .map(this::abbreviate);
    }

    public boolean hasErrorMessage() {
        return firstDisplayed(loginError, loginErrorAlt, loginErrorAlt2).isPresent();
    }

    /** El modal permanece abierto =&gt; la autenticacion fue rechazada. */
    public boolean isModalStillOpen() {
        return firstDisplayed(loginModal, loginModalAlt).isPresent();
    }

    /** El campo de contrasena se limpia tras un rechazo tipico de la pasarela de identidad. */
    public boolean isPasswordFieldCleared() {
        return firstPresent(passwordField, passwordFieldAlt, passwordFieldAlt2)
                .map(element -> {
                    String value = element.getAttribute("value");
                    return value == null || value.isEmpty();
                })
                .orElse(false);
    }

/**
     * Espera a que la cabecera confirme la sesion.
     *
     * <p>No se busca el nombre de usuario: el portal no lo expone de forma estable. La senal fiable
     * es doble: el dialogo de login desaparece y ademas aparece el boton DEPOSITAR, que el portal
     * solo renderiza para usuarios con sesion iniciada. Exigir las dos evita dar por bueno un
     * login que en realidad solo cerro el dialogo.</p>
     */
    public boolean isLoggedIn(String expectedUsername) {
        return waitUntilTrue(d -> {
            boolean rejected = loginRejectedMarkers()
                    .anyMatch(by -> !d.findElements(by).isEmpty());
            if (rejected) {
                return false;
            }
            boolean dialogClosed = d.findElements(By.xpath(SCOPE_DIALOG)).stream().noneMatch(this::isDisplayed);
            boolean depositAvailable = d.findElements(DEPOSIT_ENTRY_XP)
                    .stream().anyMatch(this::isDisplayed);
            return dialogClosed && depositAvailable;
        }, explicitWait());
    }

    /** Marcadores que delatan un rechazo de credenciales sin depender del texto exacto. */
    private Stream<By> loginRejectedMarkers() {
        return Stream.of(
                // Aviso de bloqueo por intentos fallidos / reinicio de contrasena.
                anyText("RESTABLECER", "MAXIMO DE INTENTOS"),
                By.cssSelector("[aria-invalid='true']"),
                By.cssSelector("[role='alert']"));
    }

    /** Espera a que el modal de login desaparezca, senal de que la sesion quedo establecida. */
    public boolean isLoginModalClosed() {
        return waitForInvisibility(By.xpath(SCOPE_DIALOG), explicitWait());
    }

    private WebElement require(WebElement... candidates) {
        return firstDisplayed(candidates).orElseThrow(
                () -> new org.openqa.selenium.NoSuchElementException(
                        "No se encontro ningun localizador alterno del elemento solicitado"));
    }

    private boolean waitForInvisibilityByCss(String css, java.time.Duration timeout) {
        return waitUntilTrue(d -> d.findElements(org.openqa.selenium.By.cssSelector(css)).stream()
                .noneMatch(WebElement::isDisplayed), timeout);
    }
}