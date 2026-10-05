package com.casino.qa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Contexto de Spring del framework de pruebas.
 *
 * <p>El reto no necesita exponer un servidor HTTP: Spring Boot se usa como contenedor de
 * Inversion of Control y de gestion de configuracion ({@code @ConfigurationProperties}) para que
 * las paginas, los steps y el WebDriver sean beans administrados por el framework.</p>
 *
 * <p>Requiere JDK 21 (build con {@code maven.compiler.release=21}). El arranque lo dispara
 * {@link com.casino.qa.runner.CucumberSpringContext} desde el runner de la suite; no se invoca
 * como aplicacion porque el POM ya no empaqueta el plugin de Spring Boot (proyecto 100% test).</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.casino.qa.config")
public class CasinoQaApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CasinoQaApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        System.setProperty("server.port", "-1");
        app.run(args);
    }
}