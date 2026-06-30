package com.example.chat.util;

import com.example.chat.exception.AuthException;

/** Password policy validation (single place). */
public final class Passwords {

    public static final int MIN_LENGTH = 6;
    public static final int MAX_LENGTH = 200;

    private Passwords() {
    }

    public static void requireValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new AuthException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            throw new AuthException("Password is too long");
        }
    }
}
