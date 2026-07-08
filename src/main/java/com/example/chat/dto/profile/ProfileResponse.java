package com.example.chat.dto.profile;

import com.example.chat.model.UserAccount;

public record ProfileResponse(String username, String about, long createdAt) {

    public static ProfileResponse from(UserAccount account) {
        return new ProfileResponse(
                account.getUsername(),
                account.getAbout() == null ? "" : account.getAbout(),
                account.getCreatedAt());
    }
}
