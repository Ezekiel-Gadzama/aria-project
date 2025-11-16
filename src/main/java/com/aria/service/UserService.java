package com.aria.service;

import com.aria.core.model.User;
import com.aria.storage.DatabaseManager;

public class UserService {
    private DatabaseManager dbManager;
    private User user;

    public UserService(DatabaseManager dbManager, User user) {
        this.dbManager = dbManager;
        this.user = user;
    }

    public boolean registerUser() {
        try {
            user.setUserAppId(dbManager.saveUser(user));
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to register user", e);
        }
    }

    /**
     * Attempt a login by phone or email.
     * - If identifier contains '@' we treat it as email (stored in username).
     * - Otherwise, we treat it as phone.
     * Returns the found user or null if not found.
     */
    public User login(String identifier) {
        try {
            // Deprecated plain lookup; use password-based verification instead via controller
            return DatabaseManager.getUserByPhone(identifier);
        } catch (Exception e) {
            throw new RuntimeException("Failed to login user", e);
        }
    }

    public User getUser() {
        return user;
    }

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }
}