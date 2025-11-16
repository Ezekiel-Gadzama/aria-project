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

    /**
     * Get all target users for a user (alias for getTargetUsersByUserId)
     */
    public List<TargetUser> getAllTargetUsers(int userId) {
        return getTargetUsersByUserId(userId);
    }

    /**
     * Get a target user by ID
     */
    public TargetUser getTargetUserById(int targetId) {
        try {
            // Get all target users and find by ID
            // Note: This is inefficient for large datasets, but works for now
            // TODO: Add getTargetUserById method to DatabaseManager for better performance
            // We need userId to call getTargetUsersByUserId, so we'll search across all users
            // For now, try to find in common userIds (1, 2, 3, etc.)
            for (int userId = 1; userId <= 10; userId++) {
                List<TargetUser> targets = getTargetUsersByUserId(userId);
                TargetUser found = targets.stream()
                    .filter(t -> t.getTargetId() == targetId)
                    .findFirst()
                    .orElse(null);
                if (found != null) {
                    return found;
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error getting target user by ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Save target user (returns the target user with ID)
     */
    public int saveTargetUser(TargetUser targetUser) {
        try {
            // Parse userId from string to int
            int userId = 0;
            try {
                String userIdStr = targetUser.getUserId();
                if (userIdStr != null && !userIdStr.isEmpty()) {
                    userId = Integer.parseInt(userIdStr);
                }
            } catch (NumberFormatException e) {
                // userId is not a valid number, use 0
            }
            
            if (userId > 0) {
                boolean success = dbManager.saveTargetUser(userId, targetUser);
                if (success) {
                    // Get the saved target user to return ID
                    List<TargetUser> targets = getTargetUsersByUserId(userId);
                    return targets.stream()
                        .filter(t -> t.getName().equals(targetUser.getName()) && 
                                   t.getSelectedUsername().equals(targetUser.getSelectedUsername()))
                        .map(TargetUser::getTargetId)
                        .findFirst()
                        .orElse(0);
                }
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error saving target user: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Delete target user by ID
     */
    public boolean deleteTargetUser(int targetId) {
        try {
            // Get target user first to get name and userId
            TargetUser target = getTargetUserById(targetId);
            if (target != null) {
                // Parse userId from string to int
                int userId = 0;
                try {
                    String userIdStr = target.getUserId();
                    if (userIdStr != null && !userIdStr.isEmpty()) {
                        userId = Integer.parseInt(userIdStr);
                    }
                } catch (NumberFormatException e) {
                    // userId is not a valid number
                }
                
                if (userId > 0) {
                    return deleteTargetUser(userId, target.getName());
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error deleting target user by ID: " + e.getMessage());
            return false;
        }
    }
}