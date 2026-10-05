package com.casino.qa.runner;

import com.casino.qa.CasinoQaApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Puente entre Cucumber y Spring: habilita el contexto de Spring para todos los escenarios.
 *
 * <p>{@code @SpringBootTest} inicializa el contenedor IoC, las propiedades tipadas
 * ({@code @ConfigurationPropertiesScan}) y los beans de Selenium. No se levanta servidor HTTP
 * gracias a {@code WebApplicationType.NONE} (ver {@link CasinoQaApplication}).</p>
 *
 * <p>Vive en {@code runner} porque forma parte de la configuracion de ejecucion de la suite.</p>
 */
@CucumberContextConfiguration
@SpringBootTest(classes = CasinoQaApplication.class)
public class CucumberSpringContext {
}