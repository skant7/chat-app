package com.example.chat.dto.auth;

public record ResetPasswordRequest(String token, String resetToken, String newPassword, String password) {

    public String resolvedToken() {
        if (token != null && !token.isBlank()) {
            return token;
        }
        return resetToken == null ? "" : resetToken;
    }

    public String resolvedNewPassword() {
        if (newPassword != null && !newPassword.isBlank()) {
            return newPassword;
        }
        return password == null ? "" : password;
    }
}
