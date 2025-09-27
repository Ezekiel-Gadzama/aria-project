package com.aria.platform;

public class UserPlatform {
    private String username;
    private String number;
    private int platformId;
    private Platforms platform;

    // No-args constructor (default)
    public UserPlatform() {}

    // All-args constructor
    public UserPlatform(String username, String number, int platformId, Platforms platform) {
        this.username = username;
        this.number = number;
        this.platformId = platformId;
        this.platform = platform;
    }

    // Getters and Setters

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public int getPlatformId() {
        return platformId;
    }

    public void setPlatformId(int platformId) {
        this.platformId = platformId;
    }

    public Platforms getPlatform() {
        return platform;
    }

    public void setPlatform(Platforms platform) {
        this.platform = platform;
    }
}

