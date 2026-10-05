package com.casino.qa.pages;

import com.casino.qa.config.DriverProvider;
import com.casino.qa.pages.components.HeaderComponent;
import com.casino.qa.utils.ScenarioEvidenceRecorder;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Page Object de la pagina de inicio del casino (usuario anonimo).
 *
 * <p>Responsabilidades: navegacion inicial, exposicion del modal de login y navegacion al
 * modulo de Deposito una vez autenticado.</p>
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class HomePage extends BasePage {

    /** Contenedor del modal: el portal monta sus dialogs en #app-modal, mas el design system. */
    @FindBy(css = "#app-modal")
    private WebElement loginModal;
    @FindBy(xpath = "//*[contains(@class,'clmc-popup')" + VISIBLE + "]")
    private WebElement loginModalAlt;
    @FindBy(xpath = "//*[@role='dialog'" + VISIBLE + "]")
    private WebElement loginModalAlt2;

    /** Contenedor del modal de deposito, reutilizado al abrir el modulo desde el header. */
    @FindBy(xpath = "//*[contains(@class,'clmc-modal') or contains(@class,'clmc-popup')" + VISIBLE + "]")
    private WebElement depositModal;
    @FindBy(xpath = "//*[@role='dialog'" + VISIBLE + "]")
    private WebElement depositModalAlt;

    private final HeaderComponent header;

    public HomePage(DriverProvider driverProvider, ScenarioEvidenceRecorder evidence, HeaderComponent header) {
        super(driverProvider, evidence);
        this.header = header;
    }

    /** Abre la raiz del sitio con la URL configurada en {@code qa.base-url}. */
    public HomePage open() {
        navigateTo(driverProvider.config().baseUrl());
        waitForDocumentReady(explicitWait());
        // El portal superpone aviso de cookies y promo con un dimmer fijo: sin cerrarlos, el
        // primer clic (el propio boton de acceso) queda interceptado.
        dismissOverlays();
        return this;
    }

    public HomePage open(String absoluteUrl) {
        navigateTo(absoluteUrl);
        waitForDocumentReady(explicitWait());
        dismissOverlays();
        return this;
    }

    /** Espera a que el documento termine de cargar (evita correr sobre el skeleton loader). */
    public void waitForDocumentReady(Duration timeout) {
        waitUntilOrTimeout("document readyState == complete",
                d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")),
                timeout);
    }

    public HomePage openLoginModal() {
        // La promo aparece de forma asincrona tras la carga, asi que se cierra otra vez justo
        // antes del primer clic en lugar de confiar solo en el arranque.
        dismissOverlays();
        header.clickLoginEntry();
        waitForVisibilityOfAny(loginModal, loginModalAlt, loginModalAlt2);
        return this;
    }

    public HomePage openDepositModule() {
        header.clickDeposit();
        waitForVisibilityOfAny(depositModal, depositModalAlt);
        return this;
    }

    public boolean isLoginEntryVisible() {
        return header.isLoginEntryVisible();
    }

    public boolean isDepositEntryVisible() {
        return header.isDepositEntryVisible();
    }

    public String loggedUserLabel() {
        return header.loggedUserLabel();
    }

    public String currentUrl() {
        return driver().getCurrentUrl();
    }

    public String title() {
        return driver().getTitle();
    }

    public HeaderComponent header() {
        return header;
    }
}