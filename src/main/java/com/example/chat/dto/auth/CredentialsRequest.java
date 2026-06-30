package com.example.chat.dto.auth;

/** Register / login body. */
public record CredentialsRequest(String username, String password) {

    public String usernameOrEmpty() {
        return username == null ? "" : username;
    }

    public String passwordOrEmpty() {
        return password == null ? "" : password;
    }
}
