package com.aria.core.model;

import com.aria.platform.instagram.InstagramConnector;
import com.aria.platform.telegram.TelegramConnector;
import com.aria.platform.whatsapp.WhatsappConnector;

public class User {
    private int userAppId;
    private String phone;
    private String username;
    private String firstName;
    private String lastName;
    private String appGoal;
    private TelegramConnector telegramConnector;
    private WhatsappConnector whatsappConnector;
    private InstagramConnector instagramConnector;

    public User(String phone, String username, String firstName, String lastName, String appGoal) {
        this.phone = phone;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.appGoal = appGoal;
    }

    // --- Getters ---
    public InstagramConnector getInstagramConnector() {
        return instagramConnector;
    }

    public void setInstagramConnector(InstagramConnector instagramConnector) {
        this.instagramConnector = instagramConnector;
    }

    public TelegramConnector getTelegramConnector() {
        return telegramConnector;
    }

    public void setTelegramConnector(TelegramConnector telegramConnector) {
        this.telegramConnector = telegramConnector;
    }

    public void setWhatsappConnector(WhatsappConnector whatsappConnector) {
        this.whatsappConnector = whatsappConnector;
    }

    public WhatsappConnector getWhatsappConnector() {
        return whatsappConnector;
    }

    public int getUserAppId() {
        return userAppId;
    }

    public String getPhone() {
        return phone;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getAppGoal() {
        return appGoal;
    }

    // --- Setters ---
    public void setUserAppId(int userAppId) {
        this.userAppId = userAppId;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setAppGoal(String appGoal) {
        this.appGoal = appGoal;
    }
}
