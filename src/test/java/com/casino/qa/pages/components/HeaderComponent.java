package com.casino.qa.pages.components;

import com.casino.qa.config.DriverProvider;
import com.casino.qa.pages.BasePage;
import com.casino.qa.utils.ScenarioEvidenceRecorder;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Componente reutilizable: cabecera (header) del portal.
 *
 * <p>Aplicacion del patron POM por composicion: los elementos compartidos por varias paginas
 * (cabecera, menu de cuenta) se extraen a "page components" con el mismo contrato que un
 * Page Object, evitando duplicar localizadores.</p>
 *
 * <p><b>Estrategia de localizacion (portal real inspeccionado).</b> La aplicacion es Tailwind +
 * CSS Modules: las clases de los componentes son hasheadas por build
 * ({@code MainHeader_clmc-header__4kvsM}) y cambian en cada release, y los unicos
 * {@code data-testid} son los de los iconos Material.
 * Por eso los XPath son <em>dinamicos</em>: nunca comparan texto exacto ni clases exactas, sino
 * que usan {@code contains()} sobre el texto normalizado y|traducido a mayusculas, y
 * {@code contains()} sobre {@code @class} con el prefijo estable del design system
 * ({@code clmc-}). Se evitan acentos en las palabras clave justamente para no depender de
 * {@code translate()} con acentos.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class HeaderComponent extends BasePage {

    // ------------------------------------------------------------------ localizadores
    //
    // Hallazgo del portal real: el boton de acceso es <button id="login"> y dentro tiene dos
    // <span> con el mismo rotulo ("INGRESAR" en movil, "INICIAR SESION" en escritorio) que se
    // ocultan por Tailwind segun el viewport. Por eso el texto se busca con ".//" y se devuelve
    // el ANCESTRO clickeable, nunca el <span>: este ultimo puede estar oculto y por tanto no
    // clicable. Ojo tambien a que el HTML llega con acentos rotos ("SESIA"N"), por eso las
    // palabras clave van sin tilde.

    /** Boton de acceso: id estable del portal. */
    @FindBy(css = "button#login")
    private WebElement loginEntry;
    @FindBy(xpath = "//button[.//*[contains(" + UPPER_XP + ",'INGRESAR')]]")
    private WebElement loginEntryAlt;
    @FindBy(xpath = "//*[@role='button'][.//*[contains(" + UPPER_XP + ",'INGRESAR') or contains(" + UPPER_XP + ",'INICIAR')]]")
    private WebElement loginEntryAlt2;
    @FindBy(xpath = "//*[@id='main-header']//a[contains(@href,'login')]")
    private WebElement loginEntryAlt3;

    // Boton DEPOSITAR: solo existe cuando hay sesion iniciada, asi que no hay id conocido de
    // antemano; se resuelve por texto sobre el ancestro clickeable, luego por atributo.
    @FindBy(xpath = "//button[contains(" + UPPER_XP + ",'DEPOSITAR') or .//*[contains(" + UPPER_XP + ",'DEPOSITAR')]]")
    private WebElement depositButton;
    @FindBy(xpath = "//*[(self::a or @role='button') and (contains(" + UPPER_XP + ",'DEPOSITAR') or .//*[contains(" + UPPER_XP + ",'DEPOSITAR')])]")
    private WebElement depositButtonAlt;
    @FindBy(xpath = "//*[@id='main-header']//a[contains(@href,'deposit') or contains(@href,'deposito') or contains(@href,'recarga')]")
    private WebElement depositButtonAlt2;
    @FindBy(xpath = "//*[contains(@id,'DEPOSIT') or contains(@id,'deposit')]")
    private WebElement depositButtonAlt3;

    // Usuario autenticado / menu de cuenta.
    @FindBy(xpath = "//*[@id='main-header']//*[contains(@id,'USER') or contains(@id,'user')]")
    private WebElement currentUser;
    @FindBy(xpath = "//button[.//*[contains(" + UPPER_XP + ",'MI CUENTA') or contains(" + UPPER_XP + ",'PERFIL')]]")
    private WebElement currentUserAlt;
    @FindBy(xpath = "//*[@id='main-header']//*[contains(@class,'avatar') or contains(@class,'Avatar')]")
    private WebElement currentUserAlt2;

    // Menu lateral de cuenta: el portal lo monta siempre en el DOM con estos ids.
    @FindBy(css = "#MENU_TH")
    private WebElement accountMenu;
    @FindBy(css = "#OPEN_MENU_TH")
    private WebElement accountMenuAlt;
    @FindBy(xpath = "//button[.//*[contains(" + UPPER_XP + ",'MI CUENTA')]]")
    private WebElement accountMenuAlt2;

    @FindBy(xpath = "//*[.//*[contains(" + UPPER_XP + ",'CERRAR SESION') or contains(" + UPPER_XP + ",'LOGOUT')]]")
    private WebElement logoutLink;
    @FindBy(xpath = "//*[@id='MENU_TH']//a[contains(@href,'logout') or contains(@href,'salir')]")
    private WebElement logoutLinkAlt;

    public HeaderComponent(DriverProvider driverProvider, ScenarioEvidenceRecorder evidence) {
        super(driverProvider, evidence);
    }

    /** Abre el modal de Iniciar sesion desde la cabecera. */
    public void clickLoginEntry() {
        clickWithRetry(3, loginEntry, loginEntryAlt, loginEntryAlt2, loginEntryAlt3);
    }

    /** Abre el modulo de Deposito desde la cabecera (boton "DEPOSITAR"). */
    public void clickDeposit() {
        clickWithRetry(3, depositButton, depositButtonAlt, depositButtonAlt2, depositButtonAlt3);
    }

    public boolean isLoginEntryVisible() {
        return firstDisplayed(loginEntry, loginEntryAlt, loginEntryAlt2).isPresent();
    }

    public boolean isDepositEntryVisible() {
        return firstDisplayed(depositButton, depositButtonAlt, depositButtonAlt2, depositButtonAlt3).isPresent();
    }

    /** Texto del usuario autenticado en la cabecera. Vacio si la sesion es anonima. */
    public String loggedUserLabel() {
        return firstDisplayed(currentUser, currentUserAlt, currentUserAlt2)
                .map(WebElement::getText)
                .orElse("");
    }

    /** Abre el menu de cuenta del usuario autenticado, si esta disponible. */
    public void openAccountMenu() {
        firstDisplayed(accountMenu, accountMenuAlt).ifPresent(WebElement::click);
    }

    /** Cierra la sesion abierta. */
    public void logout() {
        openAccountMenu();
        clickAny(logoutLink, logoutLinkAlt);
    }
}