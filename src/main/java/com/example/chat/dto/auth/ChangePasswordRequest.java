package com.example.chat.dto.auth;

public record ChangePasswordRequest(String currentPassword, String newPassword) {

    public String currentPasswordOrEmpty() {
        return currentPassword == null ? "" : currentPassword;
    }

    public String newPasswordOrEmpty() {
        return newPassword == null ? "" : newPassword;
    }
}
