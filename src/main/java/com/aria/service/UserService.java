package com.aria.service;

import com.aria.core.model.User;
import com.aria.storage.DatabaseManager;

public class UserService {
    private final DatabaseManager db;
    private final User user;

    public UserService(DatabaseManager db, User user) {
        this.db = db;
        this.user = user;
        this.registerUser(user);
    }

    public void registerUser(User user) {
        try {
            if (db.userExists(user.getPhone())) {
                return; // already registered
            }
            user.setUserAppId(db.saveUser(user));
        } catch (Exception e) {
            throw new RuntimeException("Failed to register user", e);
        }
    }

    public User getUser(){
        return user;
    }

}
