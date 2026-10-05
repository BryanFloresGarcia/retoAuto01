package com.casino.qa.runner;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runner dedicado a las pruebas de performance (tag {@code @performance}).
 *
 * <p>Ejecucion por Maven: {@code mvn verify -Pperformance} (Failsafe) o
 * {@code mvn test -Dcucumber.filter.tags=@performance -Dcucumber.parallel=true}.
 * Requiere un navegador por hilo: el driver se resuelve con {@code ThreadLocal}
 * (ver {@code DriverProvider}), por lo que la suite es segura en paralelo.</p>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.FILTER_TAGS_PROPERTY_NAME, value = "@performance")
@ConfigurationParameter(key = Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, value = "true")
@ConfigurationParameter(key = Constants.PARALLEL_CONFIG_STRATEGY_PROPERTY_NAME, value = "dynamic")
public class LoadTestRunner {
}