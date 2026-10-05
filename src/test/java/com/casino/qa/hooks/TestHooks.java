package com.casino.qa.hooks;

import com.casino.qa.config.DriverProvider;
import com.casino.qa.config.TestConfig;
import com.casino.qa.utils.ScenarioEvidenceRecorder;
import com.casino.qa.utils.ScenarioState;
import com.casino.qa.utils.ScreenRecorder;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Hooks transversales de la suite: ciclo de vida del navegador y de la evidencia.
 *
 * <p>Orden de operaciones por escenario ({@code @Before}):</p>
 * <ol>
 *   <li>Se enlaza el {@link Scenario} al hilo para poder adjuntar evidencia.</li>
 *   <li>Si la grabacion esta habilitada y la suite no corre en paralelo, arranca el
 *       {@link ScreenRecorder} (que ademas valida que ffmpeg exista).</li>
 * </ol>
 *
 * <p>Orden de operaciones ({@code @After}):</p>
 * <ol>
 *   <li>Captura del PNG de fallo y parada del grabador: ambas cosas necesitan el driver vivo.</li>
 *   <li>Cierre del navegador y desvinculacion del escenario.</li>
 * </ol>
 *
 * <p><b>Nota de arquetipo:</b> las clases de glue de Cucumber <strong>no</strong> llevan
 * {@code @Component}; Cucumber ya las registra y Spring volveria a definirlas, provocando beans
 * duplicados. Por eso las dependencias se reciben por constructor.</p>
 *
 * <p><b>Nota de concurrencia:</b> la grabacion por polling usa el driver desde un hilo distinto
 * al del test y {@code WebDriver} no es thread-safe. Por eso el video se desactiva
 * automaticamente cuando {@code cucumber.execution.parallel.enabled=true}. En una suite paralela
 * la evidencia queda en capturas PNG, que si son seguras.</p>
 */
public class TestHooks {

    private static final Logger LOG = LoggerFactory.getLogger(TestHooks.class);

    private final DriverProvider driverProvider;
    private final TestConfig config;
    private final ScenarioEvidenceRecorder evidence;
    private final ScenarioState state;

    private ScreenRecorder recorder;

    public TestHooks(DriverProvider driverProvider,
                     TestConfig config,
                     ScenarioEvidenceRecorder evidence,
                     ScenarioState state) {
        this.driverProvider = driverProvider;
        this.config = config;
        this.evidence = evidence;
        this.state = state;
    }

    @Before
    public void beforeScenario(Scenario scenario) {
        evidence.bind(scenario);
        // El navegador se abre aqui, no en el primer paso: los Page Objects se crean bajo demanda
        // al inicio de cada paso y deben encontrar ya un driver vivo al inicializar sus proxies
        // @FindBy. Ademas fija de forma explicita el orden "driver -> pagina -> interaccion".
        driverProvider.get();
        startRecording(scenario);
    }

    @After
    public void afterScenario(Scenario scenario) {
        try {
            if (scenario.isFailed() && driverProvider.isActive() && config.evidence().screenshotOnFailure()) {
                evidence.capture(driverProvider.get(), "escenario-fallido");
                // El markup es lo que permite reconstruir el localizador que fallo.
                evidence.capturePageSource(driverProvider.get(), "escenario-fallido");
            }
            stopRecording(scenario);
        } finally {
            driverProvider.quit();
            evidence.clear();
            state.reset();
        }
    }

    private void startRecording(Scenario scenario) {
        if (!config.evidence().videoEnabled()) {
            return;
        }
        if (Boolean.parseBoolean(System.getProperty("cucumber.execution.parallel.enabled", "false"))) {
            if (config.evidence().videoRequired()) {
                throw new IllegalStateException(
                        "qa.evidence.video-required=true pero la suite corre en paralelo. WebDriver no es "
                                + "thread-safe, por lo que la grabacion no puede usarse. Opciones: ejecutar con "
                                + "-Dcucumber.execution.parallel.enabled=false, o poner "
                                + "qa.evidence.video-required=false.");
            }
            LOG.warn("[VIDEO] desactivado: la suite corre en paralelo y WebDriver no es thread-safe");
            return;
        }
        try {
            recorder = new ScreenRecorder(config);
            recorder.start(driverProvider.get(), ScenarioEvidenceRecorder.slug(scenario));
        } catch (RuntimeException e) {
            recorder = null;
            if (config.evidence().videoRequired()) {
                // Decision de proyecto: ffmpeg es obligatorio, el fallo se propaga.
                throw e;
            }
            LOG.warn("[VIDEO] grabacion omitida (video-required=false): {}", e.getMessage());
        }
    }

    private void stopRecording(Scenario scenario) {
        if (recorder == null || !recorder.isActive()) {
            return;
        }
        Path video = recorder.stop();
        if (video != null) {
            LOG.info("[VIDEO] escenario '{}' -> {}", scenario.getName(), video);
        }
    }
}