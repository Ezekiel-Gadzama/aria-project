package com.aria.core;

import com.aria.storage.DatabaseManager;
import com.aria.storage.DatabaseSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes the application on startup
 */
public class ApplicationInitializer {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationInitializer.class);

    public static void initialize() {
        try {
            logger.info("Initializing ARIA application...");
            
            // STEP 1: Initialize base tables first (users, dialogs, messages, target_users, etc.)
            // These tables must exist before DatabaseSchema creates tables that reference them
            logger.info("Initializing base database tables...");
            DatabaseManager.ensureInitialized(); // Creates base tables (users, dialogs, target_users, etc.)
            
            // STEP 2: Initialize enhanced schema (goals, chat_categories, etc.) that references base tables
            logger.info("Initializing enhanced database schema...");
            DatabaseSchema.initializeSchema();
            logger.info("Database schema initialized successfully.");
            
            logger.info("ARIA application initialized successfully.");
        } catch (Exception e) {
            logger.error("Failed to initialize application", e);
            throw new RuntimeException("Application initialization failed", e);
        }
    }
}

