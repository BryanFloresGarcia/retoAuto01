package com.casino.qa.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Fabrica de instancias de {@link WebDriver}.
 *
 * <p>Aplica la Politica Transversal de Configuracion (diseño transversal de Selenium): todos los
 * timeouts, flags y opciones del navegador se centralizan aqui y se leen de {@code application.yml}
 * mediante {@link TestConfig}. Si un Page Object necesita cambiar un timeout, se resuelve por
 * scenario data, nunca editando el codigo.</p>
 */
@Component
public class DriverBuilder {

    /** Flags de estabilidad de renderizado para evitar falsos negativos en las aserciones visuales. */
    private static final List<String> CHROME_STABILITY_FLAGS = List.of(
            "--disable-search-engine-choice-screen",
            "--disable-features=Translate,OptimizationHints,MediaRouter,InterestFeedContentSuggestions",
            "--disable-background-networking",
            "--disable-notifications",
            "--disable-popup-blocking",
            "--no-first-run",
            "--no-default-browser-check",
            "--no-sandbox",
            "--window-size=1920,1080"
    );

    /**
     * Preferencias que desactivan los dialogos nativos del navegador.
     *
     * <p>No son cosmeticos: la burbuja "¿Quieres guardar la contraseña?" y el aviso de ubicacion
     * se renderizan fuera del DOM, encima de la pagina. No se pueden locatear ni esperar con
     * Selenium, tapan elementos en las capturas y ademas robar el foco al escenario, produciendo
     * fallos que parecen intermitentes. Se resuelven en el navegador, no en el codigo de prueba.
     *
     * <p>El valor {@code 2} es "bloquear" en el modelo de permisos de contenido de Chrome.</p>
     */
    private static final Map<String, Object> CHROME_QUIET_PREFERENCES = Map.ofEntries(
            // Geolocalizacion: se bloquea con las dos claves porque Chrome honra la que aplica.
            Map.entry("profile.default_content_setting_values.geolocation", 2),
            Map.entry("profile.managed_default_content_settings.geolocation", 2),
            // Gestor de contrasenas: sin esto aparece "¿Guardar contraseña?" en cada login.
            Map.entry("credentials_enable_service", false),
            Map.entry("profile.password_manager_enabled", false),
            Map.entry("profile.password_manager_leak_detection", false),
            // Otras autorizaciones que el portal podria pedir y que abririan igual un dialogo.
Map.entry("profile.default_content_setting_values.notifications", 2),
            Map.entry("profile.default_content_setting_values.camera", 2),
            Map.entry("profile.default_content_setting_values.microphone", 2),
            // Evitar el dialogo de descarga y el de "abrir aplicacion externa".
            Map.entry("download.prompt_for_download", false),
            Map.entry("profile.default_content_setting_values.popups", 2));

    public WebDriver build(TestConfig config) {
        String type = config.browser().type().toLowerCase(Locale.ROOT);
        WebDriver driver = switch (type) {
            case "chrome", "google-chrome", "chromium" -> new ChromeDriver(chromeOptions(config));
            case "edge" -> new EdgeDriver(edgeOptions(config));
            case "firefox" -> new FirefoxDriver(firefoxOptions(config));
            default -> throw new IllegalArgumentException(
                    "Navegador no soportado: " + config.browser().type());
        };

        applyTimeouts(driver, config);
        return driver;
    }

    private void applyTimeouts(WebDriver driver, TestConfig config) {
        TestConfig.Timeouts t = config.timeouts();
        driver.manage().timeouts().implicitlyWait(t.implicit());
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.browser().pageLoadTimeoutSec()));
        if (config.browser().windowSize() != null && !config.browser().windowSize().isBlank()) {
            applyWindowSize(driver, config.browser().windowSize());
        }
    }

    private void applyWindowSize(WebDriver driver, String size) {
        try {
            int[] dims = Arrays.stream(size.split("x")).mapToInt(Integer::parseInt).toArray();
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(dims[0], dims[1]));
        } catch (RuntimeException e) {
            // El flag --window-size del navegador ya cubre el caso; no es un error bloqueante.
        }
    }

    private ChromeOptions chromeOptions(TestConfig config) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(CHROME_STABILITY_FLAGS.toArray(String[]::new));
        // Geolocalizacion y guardado de contrasena: se desactivan aqui, no en el codice de prueba.
        options.setExperimentalOption("prefs", CHROME_QUIET_PREFERENCES);
        if (config.browser().headless()) {
            options.addArguments("--headless=new");
        }
        options.setExperimentalOption("excludeSwitches", java.util.List.of("enable-automation"));
        return options;
    }

    private EdgeOptions edgeOptions(TestConfig config) {
        EdgeOptions options = new EdgeOptions();
        if (config.browser().headless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments(CHROME_STABILITY_FLAGS.toArray(String[]::new));
        // Edge es Chromium: comparte el mismo modelo de preferencias que Chrome.
        options.setExperimentalOption("prefs", CHROME_QUIET_PREFERENCES);
        return options;
    }

    private FirefoxOptions firefoxOptions(TestConfig config) {
        FirefoxOptions options = new FirefoxOptions();
        if (config.browser().headless()) {
            options.addArguments("-headless");
        }
        // Equivalente en Firefox: permisos en 2 (bloquear) y gestor de contrasenas apagado.
        options.addPreference("permissions.default.geolocation", 2);
        options.addPreference("permissions.default.notifications", 2);
        options.addPreference("signon.rememberSignons", false);
        options.addPreference("signon.autofillForms", false);
        options.addPreference("browser.contentblocking.category", "standard");
        return options;
    }

    private void mergeUserArguments(ChromeOptions options, TestConfig config) {
        // No aplica en este modelo de Browser (no hay campo arguments). Se deja por compatibilidad.
    }

    /** Permite inyectar un driver externo (contenedores, grid, Sauce Labs, etc.). */
    public WebDriver buildFromService(WebDriver driver, TestConfig config) {
        applyTimeouts(driver, config);
        return driver;
    }

    static ChromeDriverService defaultService() {
        return null;
    }
}