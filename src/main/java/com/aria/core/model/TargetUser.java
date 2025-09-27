// TargetUser.java
package com.aria.core.model;

import com.aria.platform.UserPlatform;
import java.util.List;

public class TargetUser {
    private String name;
    private String userId;
    private List<UserPlatform> platforms;

    // Default constructor
    public TargetUser() {
    }

    // Constructor with all fields
    public TargetUser(String name, String userId, List<UserPlatform> platforms) {
        this.name = name;
        this.userId = userId;
        this.platforms = platforms;
    }

    // Constructor without userId (optional, for flexibility)
    public TargetUser(String name, List<UserPlatform> platforms) {
        this.name = name;
        this.platforms = platforms;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<UserPlatform> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(List<UserPlatform> platforms) {
        this.platforms = platforms;
    }
}


