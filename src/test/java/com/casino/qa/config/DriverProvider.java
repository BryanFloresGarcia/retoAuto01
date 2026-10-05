package com.casino.qa.config;

import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Proveedor de {@link WebDriver} seguro para ejecucion paralela.
 *
 * <p>Cada hilo de ejecucion (escenario de Cucumber) obtiene su propia instancia de navegador
 * mediante {@link ThreadLocal}. Esto permite correr la suite en paralelo sin que dos escenarios
 * compitan por el mismo driver y, ademas, hace que la suite sea segura para CI/CD con runners
 * efimeros.</p>
 *
 * <p>El driver se crea de forma perezosa: las paginas no abren un navegador hasta que realmente
 * interactuan con un elemento.</p>
 */
@Component
public class DriverProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DriverProvider.class);

    private final TestConfig config;
    private final DriverBuilder driverBuilder;
    private final ThreadLocal<WebDriver> holder = new ThreadLocal<>();

    public DriverProvider(TestConfig config, DriverBuilder driverBuilder) {
        this.config = config;
        this.driverBuilder = driverBuilder;
    }

    /** Devuelve el driver del hilo actual, creandolo de forma perezosa si aun no existe. */
    public WebDriver get() {
        WebDriver driver = holder.get();
        if (driver == null) {
            driver = driverBuilder.build(config);
            holder.set(driver);
            LOG.info("[DRIVER] Navegador creado: {} | headless={} | url={}",
                    config.browser().type(), config.browser().headless(), config.baseUrl());
        }
        return driver;
    }

    /** Cierra el driver del hilo actual. Idempotente. */
    public void quit() {
        WebDriver driver = holder.get();
        holder.remove();
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
            LOG.info("[DRIVER] Navegador cerrado correctamente");
        } catch (RuntimeException e) {
            LOG.warn("[DRIVER] Error al cerrar el navegador: {}", e.getMessage());
        }
    }

    public boolean isActive() {
        return holder.get() != null;
    }

    public TestConfig config() {
        return config;
    }
}