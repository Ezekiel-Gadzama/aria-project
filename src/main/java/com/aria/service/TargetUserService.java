package com.aria.service;

import com.aria.core.model.TargetUser;
import com.aria.storage.DatabaseManager;

import java.sql.SQLException;
import java.util.List;

public class TargetUserService {
    private DatabaseManager dbManager;

    public TargetUserService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public List<TargetUser> getTargetUsersByUserId(int userId) {
        try {
            return dbManager.getTargetUsersByUserId(userId);
        } catch (SQLException e) {
            System.err.println("Error getting target users: " + e.getMessage());
            return List.of(); // Return empty list on error
        }
    }

    public boolean saveTargetUser(int userId, TargetUser targetUser) {
        try {
            return dbManager.saveTargetUser(userId, targetUser);
        } catch (SQLException e) {
            System.err.println("Error saving target user: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteTargetUser(int userId, String targetName) {
        try {
            return dbManager.deleteTargetUser(userId, targetName);
        } catch (SQLException e) {
            System.err.println("Error deleting target user: " + e.getMessage());
            return false;
        }
    }
}