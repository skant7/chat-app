package com.example.chat.util;

import com.example.chat.exception.AuthException;

/** Shared username normalization and validation (DRY across auth and chat). */
public final class Usernames {

    public static final int MAX_LENGTH = 40;

    private Usernames() {
    }

    public static String normalize(String username) {
        return username == null ? "" : username.trim();
    }

    public static void requireValid(String normalizedName) {
        if (normalizedName.isEmpty()) {
            throw new AuthException("Username is required");
        }
        if (normalizedName.length() > MAX_LENGTH) {
            throw new AuthException("Username is too long");
        }
        if (!normalizedName.matches("[A-Za-z0-9_\\- ]+")) {
            throw new AuthException("Username has invalid characters");
        }
    }
}
