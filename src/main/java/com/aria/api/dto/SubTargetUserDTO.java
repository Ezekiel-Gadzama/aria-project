package com.aria.api.dto;

import com.aria.core.model.SubTargetUser;
import com.aria.platform.Platform;

/**
 * Data Transfer Object for SubTarget Users (platform-specific instances)
 */
public class SubTargetUserDTO {
    private Integer id;
    private Integer targetUserId; // Parent Target User ID
    private String name; // Platform-specific name/nickname
    private String username;
    private Platform platform;
    private Integer platformAccountId;
    private Long platformId;
    private String number;
    private String advancedCommunicationSettings;

    public SubTargetUserDTO() {}

    public SubTargetUserDTO(SubTargetUser subTargetUser) {
        this.id = subTargetUser.getId();
        this.targetUserId = subTargetUser.getTargetUserId();
        this.name = subTargetUser.getName();
        this.username = subTargetUser.getUsername();
        this.platform = subTargetUser.getPlatform();
        this.platformAccountId = subTargetUser.getPlatformAccountId();
        this.platformId = subTargetUser.getPlatformId();
        this.number = subTargetUser.getNumber();
        this.advancedCommunicationSettings = subTargetUser.getAdvancedCommunicationSettings();
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Integer targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public Integer getPlatformAccountId() {
        return platformAccountId;
    }

    public void setPlatformAccountId(Integer platformAccountId) {
        this.platformAccountId = platformAccountId;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getAdvancedCommunicationSettings() {
        return advancedCommunicationSettings;
    }

    public void setAdvancedCommunicationSettings(String advancedCommunicationSettings) {
        this.advancedCommunicationSettings = advancedCommunicationSettings;
    }
}

