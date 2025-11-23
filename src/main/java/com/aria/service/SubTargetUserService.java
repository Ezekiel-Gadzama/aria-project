package com.aria.service;

import com.aria.core.model.SubTargetUser;
import com.aria.storage.DatabaseManager;

import java.sql.SQLException;
import java.util.List;

/**
 * Service for managing SubTarget Users (platform-specific instances of Target Users)
 */
public class SubTargetUserService {
    private DatabaseManager dbManager;

    public SubTargetUserService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public SubTargetUser getSubTargetUserById(int subtargetUserId) {
        try {
            return dbManager.getSubTargetUserById(subtargetUserId);
        } catch (SQLException e) {
            System.err.println("Error getting SubTarget user: " + e.getMessage());
            return null;
        }
    }

    public List<SubTargetUser> getSubTargetUsersByTargetUserId(int targetUserId) {
        try {
            return dbManager.getSubTargetUsersByTargetUserId(targetUserId);
        } catch (SQLException e) {
            System.err.println("Error getting SubTarget users: " + e.getMessage());
            return List.of();
        }
    }

    public int saveSubTargetUser(SubTargetUser subTargetUser) {
        try {
            return dbManager.saveSubTargetUser(subTargetUser);
        } catch (SQLException e) {
            System.err.println("Error saving SubTarget user: " + e.getMessage());
            return 0;
        }
    }

    public boolean deleteSubTargetUser(int subtargetUserId) {
        try {
            return dbManager.deleteSubTargetUser(subtargetUserId);
        } catch (SQLException e) {
            System.err.println("Error deleting SubTarget user: " + e.getMessage());
            return false;
        }
    }
}

