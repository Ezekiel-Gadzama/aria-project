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

    public User getUser() {
        return user;
    }

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }
}