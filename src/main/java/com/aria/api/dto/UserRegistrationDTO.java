package com.aria.api.dto;

/**
 * Data Transfer Object for user registration
 */
public class UserRegistrationDTO {
    private String name;
    private String email;
    private String phoneNumber;
    private String bio;
    private String password;

    // Constructors
    public UserRegistrationDTO() {}

    public UserRegistrationDTO(String name, String email, String phoneNumber, String bio) {
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.bio = bio;
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

