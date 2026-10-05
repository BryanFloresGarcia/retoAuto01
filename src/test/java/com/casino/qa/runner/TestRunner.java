package com.casino.qa.runner;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runner principal de Cucumber (JUnit Platform Suite).
 *
 * <p>El filtro de tags, el glue y los plugins NO se declaran aqui: viven en
 * {@code src/test/resources/cucumber.properties} y son sobreescribibles por linea de comandos
 * (p. ej. {@code mvn test -Dcucumber.filter.tags="@critical"}). Asi el IDE y Maven se
 * comportan igual sin duplicar configuracion.</p>
 *
 * <p>Reportes: {@code target/cucumber-report.html} y {@code target/cucumber-report/cucumber.json}.</p>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.PLUGIN_PUBLISH_ENABLED_PROPERTY_NAME, value = "false")
public class TestRunner {
}