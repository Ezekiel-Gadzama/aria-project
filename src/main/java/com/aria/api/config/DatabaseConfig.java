package com.aria.api.config;

import com.aria.core.ApplicationInitializer;
import com.aria.storage.DatabaseManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Database configuration for Spring Boot
 * Initializes database schema on startup
 */
@Configuration
public class DatabaseConfig {

    @PostConstruct
    public void initializeDatabase() {
        try {
            // Initialize application (database schema, etc.)
            ApplicationInitializer.initialize();
            System.out.println("Database initialized successfully for Spring Boot API");
        } catch (Exception e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Note: DatabaseManager uses static methods, so we don't need to create a bean
    // Controllers can use it directly, but we initialize the database here
}

