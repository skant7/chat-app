package com.example.chat.dto.profile;

public record UpdateProfileRequest(String username, String about) {

    public String usernameOrEmpty() {
        return username == null ? "" : username;
    }

    public String aboutOrEmpty() {
        return about == null ? "" : about.trim();
    }
}
