package com.casino.qa.pages;

import com.casino.qa.config.DriverProvider;
import com.casino.qa.pages.components.HeaderComponent;
import com.casino.qa.utils.ScenarioEvidenceRecorder;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Page Object del modulo de Deposito (modal abierto desde el boton "DEPOSITAR" del header).
 *
 * <p>Responsabilidad: interaccion con el modal de deposito. El flujo completo "pasarela QR +
 * monto + moneda + envio" NO vive aqui porque atraviesa dos pantallas: se compone en
 * {@code DepositSteps}. Esta pagina tampoco conoce el detalle del QR, que corresponde a
 * {@link QrDepositPage}.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DepositPage extends BasePage {

    // ---------------------------------------------------------------- localizadores
    //
    // ---------------------------------------------------------------- localizadores
    //
    // Portal real (tras inspeccionar el DOM con sesion iniciada): el modulo de Deposito NO es un
    // modal, es un contenedor que el portal monta siempre con ids estables:
    //   #DepositContainer -> lobby con las pasarelas ofrecidas
    //   #depositForm     -> formulario de monto/moneda
    //   #depositResponse -> bloque con el QR generado
    // Las tarjetas de pasarela comparten la clase semantica "depositMethod" (no hasheada, a
    // diferencia del resto del CSS Modules). Todo se acota a SCOPE_DEPOSIT para no confundir estos
    // elementos con los del resto de la pagina.

    private static final String SCOPE_DEPOSIT =
            "//*[@id='DepositContainer' or @id='depositLobby' or @id='depositForm' or @id='depositpopup' or @id='depositResponse']";
    private static final String TOK_METHOD =
            "contains(concat(' ',normalize-space(@class),' '),' depositMethod ')";
    private static final String TOK_ERROR =
            "contains(concat(' ',normalize-space(@class),' '),' clmc-error ')";
    private static final String TOK_ALERT =
            "contains(concat(' ',normalize-space(@class),' '),' clmc-alert ')";

    // Contenedor del modulo: el portal usa ids estables, no clases del design system.
    @FindBy(css = "#DepositContainer")
    private WebElement depositModal;
    @FindBy(css = "#depositLobby")
    private WebElement depositModalAlt;
    @FindBy(css = "#depositForm")
    private WebElement depositModalAlt2;

    // Pasarelas ofrecidas: las tarjetas .depositMethod del lobby.
    @FindBy(xpath = "//*[" + TOK_METHOD + "]")
    private List<WebElement> paymentMethodTiles;
    @FindBy(xpath = SCOPE_DEPOSIT + "//*[" + TOK_METHOD + "]")
    private List<WebElement> paymentMethodTilesAlt;
    @FindBy(xpath = "//*[contains(@class,'depositMethod')]")
    private List<WebElement> paymentMethodTilesAlt2;

// Pasarela "Deposito con QR". El texto real es "Deposito con QR" CON acento, asi que ni
    // "CON QR" ni "DEPOSITO" casan de forma fiable; la clave es "QR", que solo aparece en esta
    // pasarela del lobby (las demas son Interbancaria, Test TI y Debito/Credito).
    @FindBy(xpath = "//*[" + TOK_METHOD + "][.//*[contains(" + UPPER_XP + ",'QR')]]")
    private WebElement qrMethod;
    @FindBy(xpath = "//*[" + TOK_METHOD + "][contains(" + UPPER_XP + ",'QR')]")
    private WebElement qrMethodAlt;
    @FindBy(xpath = "//*[contains(@class,'depositMethod') and .//*[contains(" + UPPER_XP + ",'QR')]]")
    private WebElement qrMethodAlt2;

// Monto: el portal expone un id estable para el campo de monto libre.
    @FindBy(css = "#amountButtonInput")
    private WebElement amountField;
    @FindBy(xpath = "//*[@id='depositForm']//input[not(@type='password') and not(@type='checkbox') and not(@type='radio')]")
    private WebElement amountFieldAlt;
    @FindBy(xpath = "//input[@type='number' or @inputmode='numeric' or @inputmode='decimal' or contains(@placeholder,'monto') or contains(@id,'amount')]")
    private WebElement amountFieldAlt2;
    @FindBy(xpath = "//input[@type='text']")
    private WebElement amountFieldAlt3;

    // Moneda: <select> nativo dentro del formulario.
    @FindBy(xpath = "//*[@id='depositForm']//select")
    private WebElement currencySelect;
    @FindBy(xpath = "//select[contains(@name,'moneda') or contains(@id,'moneda') or contains(@id,'currency')]")
    private WebElement currencySelectAlt;
    @FindBy(xpath = "//select")
    private WebElement currencySelectAlt2;

// Envio: el boton real se llama "SIGUIENTE", es type=submit y cuelga de #depositButton. No esta
    // dentro de #depositForm, por eso el id del contenedor es el ancla estable.
    @FindBy(xpath = "//*[@id='depositButton']//button[contains(" + UPPER_XP + ",'SIGUIENTE') or .//*[contains(" + UPPER_XP + ",'SIGUIENTE')]]")
    private WebElement depositSubmit;
    @FindBy(xpath = "//button[contains(" + UPPER_XP + ",'SIGUIENTE') or contains(" + UPPER_XP + ",'CONTINUAR') or contains(" + UPPER_XP + ",'CONFIRMAR') or contains(" + UPPER_XP + ",'GENERAR')]")
    private WebElement depositSubmitAlt;
    @FindBy(xpath = "//button[contains(" + UPPER_XP + ",'ACEPTAR')]")
    private WebElement depositSubmitAlt2;

    // El portal marca el boton de envio con la clase "clmc-disabled" cuando el monto es invalido.
    // Es la senal de validez mas fiable: no depende del texto, que cambia entre el aviso
    // informativo ("Monto minimo por transaccion: S/10") y el error ("... es de S/10").
    @FindBy(css = "#depositButton button.clmc-disabled")
    private WebElement submitDisabledMarker;

    // Mensajes de limite del portal: uno informa del minimo y otro del maximo. Solo uno se
    // muestra cuando el monto es invalido, pero ambos estan presentes cuando el monto es valido,
    // asi que por si solos NO sirven para detectar un error.
    @FindBy(css = ".amountMinError")
    private WebElement amountMinNotice;
    @FindBy(css = ".amountMaxError")
    private WebElement amountMaxNotice;

    // Error de validacion del monto.
    @FindBy(xpath = "//*[" + TOK_ERROR + " or " + TOK_ALERT + "]")
    private WebElement amountError;
    @FindBy(xpath = "//*[@role='alert']")
    private WebElement amountErrorAlt;
    @FindBy(xpath = "//*[contains(" + UPPER_XP + ",'MONTO MINIMO') or contains(" + UPPER_XP + ",'MONTO MAXIMO') or contains(" + UPPER_XP + ",'INVALID') or contains(" + UPPER_XP + ",'INGRESE UN VALOR') or contains(" + UPPER_XP + ",'RANGO')]")
    private WebElement amountErrorAlt2;

    // Cierre del modulo (el portal lo monta siempre en el DOM).
    @FindBy(xpath = "//*[contains(@data-testid,'CloseIcon')]/ancestor::*[self::button or self::div or @role='button'][1]")
    private WebElement closeModalButton;
    @FindBy(xpath = "//button[contains(@aria-label,'Cerrar') or contains(@aria-label,'Close') or contains(@class,'clmc-close')]")
    private WebElement closeModalButtonAlt;
    @FindBy(xpath = "//*[@id='error400Popup']//*[contains(concat(' ',normalize-space(@class),' '),' clmc-pointer ')]")
    private WebElement closeModalButtonAlt2;
    private final HeaderComponent header;

    public DepositPage(DriverProvider driverProvider, ScenarioEvidenceRecorder evidence, HeaderComponent header) {
        super(driverProvider, evidence);
        this.header = header;
    }

    // ------------------------------------------------------------------ acciones

    /** Header -&gt; "DEPOSITAR" -&gt; modal de deposito. */
    public DepositPage openFromHeader() {
        header.clickDeposit();
        waitForVisibilityOfAny(depositModal, depositModalAlt, depositModalAlt2);
        return this;
    }

    /** Selecciona la pasarela "Pago con QR". */
    public DepositPage selectQrPaymentMethod() {
        clickWithRetry(3, qrMethod, qrMethodAlt, qrMethodAlt2);
        return this;
    }

    /**
     * Elige otra pasarela distinta a QR (para regresion del selector).
     *
     * <p>Busqueda dinamica por texto parcial normalizado, no igualdad exacta: el portal puede
     * cambiar el rotulo ("Tarjeta de credito", "Transferencia", ...) sin romper el escenario.</p>
     */
    public DepositPage selectPaymentMethod(String methodName) {
        String needle = methodName.toUpperCase();
        Optional<WebElement> target = firstDisplayed(
                driver().findElements(anyText(needle)).stream().toArray(WebElement[]::new));
        target.ifPresent(WebElement::click);
        return this;
    }

    /** Ingresa el monto del deposito. */
    public DepositPage enterAmount(String amount) {
        type(require(amountField, amountFieldAlt, amountFieldAlt2, amountFieldAlt3), amount);
        return this;
    }

    public DepositPage enterAmount(double amount) {
        return enterAmount(String.valueOf(amount));
    }

    /** Selecciona la moneda; si el portal no expone selector, no falla. */
    public DepositPage selectCurrency(String currencyCode) {
        Optional<WebElement> selectElement =
                firstDisplayed(currencySelect, currencySelectAlt, currencySelectAlt2);
        if (selectElement.isEmpty()) {
            return this;
        }
        Select select = select(selectElement.get());
        for (WebElement option : select.getOptions()) {
            if (option.getText().trim().equalsIgnoreCase(currencyCode)) {
                select.selectByVisibleText(currencyCode);
                return this;
            }
        }
        select.selectByValue(currencyCode);
        return this;
    }

/**
     * Envia la solicitud de deposito con la pasarela seleccionada.
     *
     * <p>Si el portal tiene el boton deshabilitado ({@code clmc-disabled}) no se pulsa: el propio
     * portal impide el envio. Esto es exactamente lo que deben comprobar los escenarios de monto
     * invalido, en los que el paso "envio la solicitud" se ejecuta y aun asi la operacion no
     * avanza. Pulsar un boton deshabilitado solo produciria una excepcion de WebDriver.</p>
     */
    public DepositPage submit() {
        if (!isAmountAccepted()) {
            LOG.info("[DEPOSITO] el boton de envio esta deshabilitado: se omite el clic");
            return this;
        }
        clickAny(depositSubmit, depositSubmitAlt, depositSubmitAlt2);
        return this;
    }

    /**
     * El portal considera valido el monto cuando deja el boton de envio sin {@code clmc-disabled}.
     *
     * <p>Es la comprobacion de referencia del modulo. Los textos de "Monto minimo/maximo" NO sirven
     * para esto porque el portal los muestra siempre, tanto en el aviso informativo como en el
     * error: lo unico que cambia de verdad es la habilitacion del boton.</p>
     */
    public boolean isAmountAccepted() {
        Optional<WebElement> submit = firstDisplayed(depositSubmit, depositSubmitAlt, depositSubmitAlt2);
        if (submit.isEmpty()) {
            return false;
        }
        String css = submit.get().getAttribute("class");
        if (css != null && css.contains("clmc-disabled")) {
            return false;
        }
        return firstDisplayed(submitDisabledMarker).isEmpty();
    }

    public void closeModal() {
        firstDisplayed(closeModalButton, closeModalButtonAlt, closeModalButtonAlt2)
                .ifPresent(WebElement::click);
    }

    // ----------------------------------------------------------------- lecturas

    public boolean isModalVisible() {
        return firstDisplayed(depositModal, depositModalAlt, depositModalAlt2).isPresent();
    }

    /** El modal permanece abierto porque la solicitud fue rechazada en cliente. */
    public boolean isModalStillOpen() {
        return isModalVisible();
    }

    /** Pasarelas de pago ofrecidas por el portal, uniendo los tres criterios alternativos. */
    public List<String> availablePaymentMethods() {
        List<WebElement> found = new ArrayList<>();
        for (List<WebElement> candidates : List.of(paymentMethodTiles, paymentMethodTilesAlt, paymentMethodTilesAlt2)) {
            List<WebElement> resolved = elementsOf(candidates);
            if (!resolved.isEmpty()) {
                found = resolved;
                break;
            }
        }
        return found.stream()
                .filter(this::isDisplayed)
                .map(WebElement::getText)
                .map(this::abbreviate)
                .filter(text -> !text.isEmpty())
                .distinct()
                .toList();
    }

    /** Mensaje de validacion del monto (rango minimo/maximo). */
/**
     * Mensaje de validacion del monto, si lo hay.
     *
     * <p>Se decide por el estado del boton de envio, no buscando textos. El portal muestra siempre
     * los limites ("Monto minimo por transaccion: S/10" y "El monto maximo ... S/500"); lo que
     * cambia con un monto invalido es que el boton de envio queda deshabilitado. Por eso, si el
     * monto es valido no hay nada que reprochar y se devuelve vacio, evitando el falso positivo
     * que rompia "el sistema acepta el monto en rango".</p>
     */
    public Optional<String> amountValidationMessage() {
        if (isAmountAccepted()) {
            return Optional.empty();
        }
        return firstDisplayed(amountMinNotice, amountMaxNotice)
                .map(WebElement::getText)
                .map(this::abbreviate)
                .filter(text -> !text.isEmpty());
    }

    public String enteredAmount() {
        return firstDisplayed(amountField, amountFieldAlt, amountFieldAlt2, amountFieldAlt3)
                .map(element -> element.getAttribute("value"))
                .orElse("");
    }

    private WebElement require(WebElement... candidates) {
        return firstDisplayed(candidates).orElseThrow(
                () -> new NoSuchElementException(
                        "No se encontro ningun localizador alterno del elemento solicitado"));
    }
}