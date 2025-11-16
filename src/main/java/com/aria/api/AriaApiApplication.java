package com.aria.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot REST API Application
 * This is the entry point for the web API version of ARIA
 * Run this to start the REST API server (port 8080)
 * 
 * Note: JavaFX UI (MainApp) is still available for local desktop use
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.aria"})
// Removed @EntityScan and @EnableJpaRepositories since we're using custom DatabaseManager instead of JPA
public class AriaApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AriaApiApplication.class, args);
    }
}

