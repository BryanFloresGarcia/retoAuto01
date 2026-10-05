package com.casino.qa.runner;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runner de reejecucion: ejecuta unicamente los escenarios registrados en
 * {@code target/rerun.txt} por el plugin {@code rerun}.
 *
 * <p><b>Por que un runner propio.</b> El motor de JUnit Platform <em>no</em> lee
 * {@code cucumber.rerun} como system property (comprobado: los 11 escenarios se ejecutaron
 * igualmente). La via soportada es declararla como parameter de configuracion de la suite, de ahi
 * que el perfil {@code rerun} del POM cambie el runner en lugar de pasar la property por linea de
 * comandos.</p>
 *
 * <p>Se limpia el filtro de tags: en un rerun interesan todos los fallos registrados, no solo
 * los de una familia.</p>
 *
 * <p>Se usan las claves literales y no {@code Constants}: la version del motor instalada no
 * expone {@code RERUN_FILE_PROPERTY_NAME}.</p>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.rerun", value = "target/rerun.txt")
@ConfigurationParameter(key = "cucumber.filter.tags", value = "")
public class ReRunTestRunner {
}