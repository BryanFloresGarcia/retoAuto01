package com.casino.qa.pages;

import com.casino.qa.config.DriverProvider;
import com.casino.qa.config.TestConfig;
import com.casino.qa.utils.ScenarioEvidenceRecorder;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Clase base del patron Page Object Model.
 *
 * <p><b>Responsabilidad unica de esta capa:</b> interaccion tecnica con el driver. Cada subclase
 * declara sus propios localizadores con {@code @FindBy} y expone metodos de lenguaje natural
 * ({@code ingresarCredenciales}).</p>
 *
 * <p><b>Reglas del arquetipo respetadas aqui:</b></p>
 * <ul>
 *   <li>Ninguna asercion en la capa de paginas: las esperas lanzan {@link TimeoutException}
 *       (error tecnico), nunca {@code AssertionError}. Las validaciones viven en
 *       {@code steps}.</li>
 *   <li>Ninguna orquestacion entre paginas: un flujo que atraviesa dos pantallas se compone en
 *       {@code steps}.</li>
 *   <li>Ningun {@code Thread.sleep} como sincronizacion: esperas explicitas parametrizadas.</li>
 *   <li>Los Page Objects son beans {@code prototype} enlazados al driver del hilo actual.</li>
 * </ul>
 *
 * <h3>Localizadores alternos y dinamicos</h3>
 * <p>El portal real ({@code https://www.casinoatlanticcity.com/}) se inspecciono y resulto ser una
 * SPA Tailwind + CSS Modules: las clases de los componentes van hasheadas por build
 * ({@code MainHeader_clmc-header__4kvsM}) y cambian en cada release, y los unicos
 * {@code data-testid} publicados son los de los iconos Material. Por eso <b>no</b> se usan
 * XPaths absolutos ni igualdad exacta de texto o de clase.</p>
 * <p>Cada elemento critico se declara como <em>varios</em> campos {@code @FindBy} y se resuelve con
 * "first match wins" en {@link #firstDisplayed(WebElement...)}. La prioridad es:
 * atributo estable ({@code id}, {@code href}, {@code name}, {@code type}, {@code placeholder}) >
 * texto parcial normalizado ({@link #anyText(String...)}) > token de clase del design system
 * ({@link #classToken(String)}). Un campo que no coincide produce un proxy de PageFactory que
 * lanza {@link NoSuchElementException} al usarlo, y ese error se captura para pasar al siguiente
 * candidato.</p>
 */
public abstract class BasePage {

    protected static final Logger LOG = LoggerFactory.getLogger(BasePage.class);

    // ------------------------------------------------- XPath dinamico (portal real)

    /**
     * Expresion reutilizable: texto del nodo normalizado (sin espacios sobrantes) y traducido a
     * mayusculas. Base de toda busqueda por texto insensible a mayus/minusculas.
     *
     * <p>Se combinan palabras clave <b>sin acentos</b> a proposito: {@code translate()} de
     * XPath 1.0 no puede eliminar tildes, y "DEPOSITAR"/"PAGO CON QR"/"INGRESAR" no las tienen,
     * de modo que el localizador no depende del idioma ni de la codificacion de la pagina.</p>
     */
    protected static final String UPPER_XP =
            "translate(normalize-space(.),'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')";

    /**
     * Plantilla de coincidencia de <b>token</b> de clase completo: {@code contains(concat(...))}
     * con espacios de guarda, para que 'clmc-user' no coincida con 'clmc-usuario'.
     */
    protected static final String CLASS_XP = "contains(concat(' ',normalize-space(@class),' '),' %s ')";

    /** Any-XPath dinamico: cualquier elemento cuyo texto normalizado contenga alguno de los terminos. */
    protected static By anyText(String... keywords) {
        StringBuilder condition = new StringBuilder();
        for (String keyword : keywords) {
            if (condition.length() > 0) {
                condition.append(" or ");
            }
            condition.append("contains(").append(UPPER_XP).append(",'")
                    .append(keyword.toUpperCase()).append("')");
        }
        return By.xpath("//*[" + condition + "]");
    }

    /** Any-XPath dinamico acotado a un contenedor (por ejemplo {@code //*[@id='main-header']}). */
    protected static By anyTextWithin(String containerXpath, String... keywords) {
        StringBuilder condition = new StringBuilder();
        for (String keyword : keywords) {
            if (condition.length() > 0) {
                condition.append(" or ");
            }
            condition.append("contains(").append(UPPER_XP).append(",'")
                    .append(keyword.toUpperCase()).append("')");
        }
        return By.xpath(containerXpath + "//*[" + condition + "]");
    }

    /** Any-XPath dinamico por token de clase del design system ({@code clmc-}). */
    protected static By classToken(String token) {
        return By.xpath("//*[" + String.format(CLASS_XP, token) + "]");
    }

/**
     * Constante utilizable dentro de {@code @FindBy}: filtra los nodos que el portal marca como
     * ocultos con su propia clase de visibilidad ({@code clmc-hidden}). Es un predicado, por lo
     * que se concatena al final de una condicion.
     */
    protected static final String VISIBLE =
            "and not(contains(concat(' ',normalize-space(@class),' '),' clmc-hidden '))";

    /**
     * Ambito de los modales del portal. Los dialogos son de Material UI ({@code MuiDialog-*}) y
     * se superponen a la pagina; sin acotar a este ambito, un XPath como "boton con texto
     * INGRESAR" acaba matcheando el boton homonimo de la cabecera, que queda debajo del
     * dialogo y por tanto no es clicable.
     */
    protected static final String SCOPE_DIALOG =
            "//*[contains(@class,'MuiDialog-container') or contains(@class,'MuiModal-root') or @role='dialog']";

    /**
     * Predicado de boton cuyo texto contiene alguno de los terminos, considerando tanto el texto
     * propio como el de sus descendientes.
     *
     * <p>El termino "propio" es imprescindible: en este portal el rotulo ("DEPOSITAR") suele ser un
     * nodo de texto directo del {@code <button>}, sin etiqueta que lo envuelva, asi que un
     * {@code .//*[contains(...)]} no lo encuentra nunca.</p>
     */
    protected static String buttonWithText(String... keywords) {
        StringBuilder condition = new StringBuilder();
        for (String keyword : keywords) {
            if (condition.length() > 0) {
                condition.append(" or ");
            }
            String upper = keyword.toUpperCase();
            condition.append("contains(").append(UPPER_XP).append(",'").append(upper).append("')")
                    .append(" or .//*[contains(").append(UPPER_XP).append(",'").append(upper).append("')]");
        }
        return "//*[(self::button or self::a or @role='button') and (" + condition + ")]";
    }

    protected final DriverProvider driverProvider;
    protected final ScenarioEvidenceRecorder evidence;

private volatile boolean proxiesInitialized;

    protected BasePage(DriverProvider driverProvider, ScenarioEvidenceRecorder evidence) {
        this.driverProvider = driverProvider;
        this.evidence = evidence;
        // Los proxies @FindBy deben existir ANTES de que nadie lea un campo de localizador: si se
        // leyeran antes, se capturaria su valor (null) en el varargs y la pagina nunca resolveria.
        // Por eso se inicializan aqui siempre que ya haya un driver vivo, que es el caso normal:
        // el hook @Before abre el navegador y las paginas se crean despues, bajo demanda.
        if (driverProvider.isActive()) {
            PageFactory.initElements(driverProvider.get(), this);
            proxiesInitialized = true;
        }
    }

    // ------------------------------------------------------------------- driver

    /**
     * Driver del hilo, con los proxies de {@code @FindBy} inicializados de forma perezosa en el
     * primer uso: construir una pagina no debe abrir un navegador.
     */
    protected WebDriver driver() {
        WebDriver driver = driverProvider.get();
        if (!proxiesInitialized) {
            PageFactory.initElements(driver, this);
            proxiesInitialized = true;
        }
        return driver;
    }

    // -------------------------------------------------------- esperas explicitas

    protected WebElement waitForVisibility(WebElement element) {
        return waitForVisibility(element, explicitWait());
    }

    protected WebElement waitForVisibility(WebElement element, Duration timeout) {
        LOG.debug("[WAIT] visible {} ms -> {}", timeout.toMillis(), describe(element));
        return fluentWait().withTimeout(timeout).until(ExpectedConditions.visibilityOf(element));
    }

    protected WebElement waitForVisibility(By by) {
        return waitForVisibility(by, explicitWait());
    }

    protected WebElement waitForVisibility(By by, Duration timeout) {
        LOG.debug("[WAIT] visible {} ms -> {}", timeout.toMillis(), by);
        return fluentWait().withTimeout(timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    protected List<WebElement> waitForPresenceOfAll(By by) {
        return fluentWait().withTimeout(explicitWait())
                .until(ExpectedConditions.presenceOfAllElementsLocatedBy(by));
    }

    protected boolean waitForInvisibility(By by) {
        return waitForInvisibility(by, explicitWait());
    }

    protected boolean waitForInvisibility(By by, Duration timeout) {
        try {
            return fluentWait().withTimeout(timeout).until(ExpectedConditions.invisibilityOfElementLocated(by));
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** Espera booleana sin excepcion, para verificaciones no bloqueantes. */
    protected boolean waitUntilTrue(Function<WebDriver, Boolean> condition, Duration timeout) {
        try {
            return Boolean.TRUE.equals(fluentWait().withTimeout(timeout).until(condition));
        } catch (TimeoutException e) {
            LOG.debug("[WAIT] condicion no cumplida en {} ms", timeout.toMillis());
            return false;
        }
    }

    protected boolean waitUntilTrue(ExpectedCondition<Boolean> condition, Duration timeout) {
        return waitUntilTrue((Function<WebDriver, Boolean>) condition, timeout);
    }

    /**
     * Espera bloqueante. Lanza {@link TimeoutException} (error tecnico) si la condicion no se
     * cumple: esta capa no decide si el test pasa o falla, solo informa que el DOM no llego.
     */
    protected void waitUntilOrTimeout(String description, Function<WebDriver, Boolean> condition, Duration timeout) {
        try {
            fluentWait().withTimeout(timeout).until(condition);
        } catch (TimeoutException e) {
            throw new TimeoutException("No se cumplio la condicion: " + description, e);
        }
    }

    protected FluentWait<WebDriver> fluentWait() {
        return new FluentWait<>(driver())
                .pollingEvery(pollInterval())
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);
    }

    protected Duration explicitWait() {
        return timeouts().explicit();
    }

    protected Duration shortWait() {
        return timeouts().shortWait();
    }

    protected Duration pollInterval() {
        return timeouts().poll();
    }

    protected TestConfig.Timeouts timeouts() {
        return driverProvider.config().timeouts();
    }

    // ------------------------------------- resolucion de localizadores alternos (@FindBy)

    /** Primer candidato visible; {@link Optional#empty()} si ninguno lo esta. */
    protected Optional<WebElement> firstDisplayed(WebElement... candidates) {
        for (WebElement candidate : candidates) {
            Optional<WebElement> resolved = resolve(candidate);
            if (resolved.isPresent() && isDisplayed(resolved.get())) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    /** Primer candidato presente en el DOM, visible o no. */
    protected Optional<WebElement> firstPresent(WebElement... candidates) {
        for (WebElement candidate : candidates) {
            Optional<WebElement> resolved = resolve(candidate);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    /** Traduce la excepcion del proxy no resoluble a Optional vacio. */
    private Optional<WebElement> resolve(WebElement candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        try {
            candidate.getTagName();
            return Optional.of(candidate);
        } catch (NoSuchElementException | StaleElementReferenceException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.debug("[LOCATOR] candidato no aplicable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
 * Espera a que alguno de los candidatos alternos sea visible y lo devuelve.
 *
 * <p>Si se agota el tiempo, registra el estado real de <em>todos</em> los candidatos (tag, id,
 * clase, texto, visibilidad y rectangulo). Con localizadores dinamicos esa informacion es la
 * unica forma de distinguir "no existe" de "existe pero oculto" o "existe con otro texto", que
 * son tres fallos de codigo muy distintos.</p>
 */
    protected WebElement waitForVisibilityOfAny(Duration timeout, WebElement... candidates) {
        try {
            return fluentWait().withTimeout(timeout).until(ignored ->
                    firstDisplayed(candidates)
                            .orElseThrow(() -> new NoSuchElementException("Ningun localizador alterno es visible")));
        } catch (TimeoutException e) {
            logCandidates(candidates);
            throw e;
        }
    }

    /** Vuelca el estado de cada candidato para diagnostico de localizadores. */
    protected void logCandidates(WebElement... candidates) {
        for (int i = 0; i < candidates.length; i++) {
            WebElement candidate = candidates[i];
            try {
                String id = candidate.getAttribute("id");
                String className = candidate.getAttribute("class");
                Rectangle rect = candidate.getRect();
                LOG.warn("[LOCATOR] candidato {} -> <{}> id='{}' class='{}' texto='{}' visible={} rect={}x{} @({},{})",
                        i, candidate.getTagName(), id,
                        className == null ? "" : abbreviate(className),
                        abbreviate(candidate.getText()),
                        isDisplayed(candidate),
                        rect.getWidth(), rect.getHeight(), rect.getX(), rect.getY());
            } catch (RuntimeException e) {
                LOG.warn("[LOCATOR] candidato {} -> no resoluble: {}", i, e.getMessage());
            }
        }
    }

    protected WebElement waitForVisibilityOfAny(WebElement... candidates) {
        return waitForVisibilityOfAny(explicitWait(), candidates);
    }

    /** Clic con reintentos acotados, para elementos con animaciones u overlays intermitentes. */
    protected void clickWithRetry(int attempts, WebElement... candidates) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                clickAny(candidates);
                return;
} catch (StaleElementReferenceException | ElementClickInterceptedException e) {
                lastError = e;
                LOG.warn("[ACTION] clic intento {}/{} fallo: {}", attempt, attempts, e.getMessage());
                // Un dimmer sincrono de promocion es la causa habitual del intercept: se cierra
                // y se reintenta en lugar de esperar a que desaparezca solo.
                dismissOverlays();
                waitUntilTrue(d -> d.findElements(By.cssSelector(".loading, .spinner, .overlay, [aria-busy='true']"))
                        .stream().noneMatch(WebElement::isDisplayed), pollInterval());
            }
        }
        throw lastError;
    }

    protected void clickAny(WebElement... candidates) {
        waitForVisibilityOfAny(candidates).click();
    }

    protected String textOfAny(WebElement... candidates) {
        return waitForVisibilityOfAny(candidates).getText();
    }

    protected String attributeOfAny(String attribute, WebElement... candidates) {
        return waitForVisibilityOfAny(candidates).getAttribute(attribute);
    }

    // ------------------------------------------------------------ interacciones

    protected void click(WebElement element) {
        waitForVisibility(element).click();
    }

    protected void type(By by, String text) {
        WebElement element = waitForVisibility(by);
        element.clear();
        if (text != null && !text.isEmpty()) {
            element.sendKeys(text);
        }
    }

    protected void type(WebElement element, String text) {
        waitForClickability(element).clear();
        if (text != null && !text.isEmpty()) {
            element.sendKeys(text);
        }
    }

    protected WebElement waitForClickability(WebElement element) {
        return fluentWait().withTimeout(explicitWait()).until(ExpectedConditions.elementToBeClickable(element));
    }

    protected boolean isDisplayed(WebElement element) {
        try {
            return element.isDisplayed();
        } catch (RuntimeException e) {
            return false;
        }
    }

    protected Optional<WebElement> findFirst(By by) {
        return driver().findElements(by).stream().findFirst();
    }

    protected List<String> textsOf(By by) {
        List<String> values = new ArrayList<>();
        for (WebElement element : driver().findElements(by)) {
            values.add(element.getText().replaceAll("\\s+", " ").trim());
        }
        return values;
    }

    protected List<WebElement> elementsOf(List<WebElement> candidates) {
        List<WebElement> found = new ArrayList<>();
        for (WebElement candidate : candidates) {
            resolve(candidate).ifPresent(found::add);
        }
        return found;
    }

    protected Select select(WebElement element) {
        return new Select(waitForVisibility(element));
    }

    // ---------------------------------------------------------------- scrolling

protected void scrollTo(WebElement element) {
        executeScript("arguments[0].scrollIntoView({block:'center'});", element);
    }

    protected Object executeScript(String script, Object... args) {
        return ((JavascriptExecutor) driver()).executeScript(script, args);
    }

    // ----------------------------------------------- frames, ventanas, navegacion

    /** Cambia a la ventana distinta al handle actual (ventana de la pasarela de pago). */
    protected void switchToNewWindow() {
        String current = driver().getWindowHandle();
        waitUntilOrTimeout("apertura de ventana emergente por parte de la pasarela",
                d -> d.getWindowHandles().size() > 1, explicitWait());
        for (String handle : driver().getWindowHandles()) {
            if (!handle.equals(current)) {
                driver().switchTo().window(handle);
                return;
            }
        }
        throw new IllegalStateException("No se detecto una ventana emergente");
    }

    protected void closeWindowAndBackToFirst() {
        driver().close();
        String firstHandle = driver().getWindowHandles().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No quedan ventanas abiertas"));
        driver().switchTo().window(firstHandle);
    }

    protected void navigateTo(String url) {
        driver().get(url);
    }

    protected void refresh() {
        driver().navigate().refresh();
    }

    protected void resizeWindow(String size) {
        int[] dimensions = Arrays.stream(size.split("x")).mapToInt(Integer::parseInt).toArray();
        driver().manage().window().setSize(new Dimension(dimensions[0], dimensions[1]));
    }

    // ----------------------------------------------------------------- capas flotantes

    /**
     * Cierra las capas que el portal superpone al contenido (aviso de cookies, promociones y
     * menu lateral).
     *
     * <p>No es cosmetico: sin esto el clic falla con
     * {@code ElementClickInterceptedException} porque un dimmer fijo ({@code #optiRealPopupDimmer},
     * con {@code z-index} de 9 cifras) queda encima del boton. Se recorren varios cierres
     * candidatos porque ninguno es obligatorio y pueden solaparse.</p>
     */
    protected void dismissOverlays() {
        List<By> closers = List.of(
                // Aviso de cookies: "ACEPTAR COOKIES".
                By.xpath("//button[.//*[contains(" + UPPER_XP + ",'ACEPTAR')]]"),
                // Promo Optimizely: la "X" blanca sobre el recuadro centrado.
                By.cssSelector("#optiRealclosePopupImage"),
                By.xpath("//*[@onclick and contains(@onclick,'closeRealtimePopup')]"),
                // Promo o menu lateral: icono de cierre de Material.
                By.xpath("//button[.//*[@data-testid='CloseIcon']]"),
                By.cssSelector("#CLOSE_MENU_TH"));

        for (By closer : closers) {
            Optional<WebElement> target = firstDisplayed(driver().findElements(closer).stream()
                    .filter(element -> element.getRect().getWidth() > 0)
                    .toArray(WebElement[]::new));
            if (target.isEmpty()) {
                continue;
            }
            try {
                target.get().click();
                LOG.info("[OVERLAY] capa cerrada con {}", closer);
                waitUntilTrue(d -> d.findElements(closer).stream().noneMatch(this::isDisplayed), shortWait());
            } catch (RuntimeException e) {
                LOG.debug("[OVERLAY] no se pudo cerrar {}: {}", closer, e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- evidencia

    /** Captura el navegador y la deja adjunta al reporte y persistida en disco. */
    public Path captureScreenshot(String label) {
        return evidence.capture(driver(), label);
    }

    // --------------------------------------------------------------- utilidades

    protected String describe(WebElement element) {
        try {
            return element.getTagName() + "[" + abbreviate(element.getText()) + "]";
        } catch (RuntimeException e) {
            return "<stale>";
        }
    }

    protected String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String flattened = value.replaceAll("\\s+", " ").trim();
        return flattened.length() > 40 ? flattened.substring(0, 37) + "..." : flattened;
    }
}