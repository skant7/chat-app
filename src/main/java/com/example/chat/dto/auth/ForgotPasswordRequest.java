package com.example.chat.dto.auth;

public record ForgotPasswordRequest(String username) {

    public String usernameOrEmpty() {
        return username == null ? "" : username;
    }
}
