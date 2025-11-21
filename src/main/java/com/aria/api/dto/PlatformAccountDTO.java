package com.aria.api.dto;

import com.aria.storage.DatabaseManager;

/**
 * DTO for platform account information exposed via API.
 */
public class PlatformAccountDTO {
    private int id;
    private String platform;
    private String username;
    private String number;
    private String accountName;

    public PlatformAccountDTO() {}

    public PlatformAccountDTO(DatabaseManager.PlatformAccount acc) {
        this.id = acc.id;
        this.platform = acc.platform;
        this.username = acc.username;
        this.number = acc.number;
        this.accountName = acc.accountName;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

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

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }
}


