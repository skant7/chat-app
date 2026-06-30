package com.example.chat.exception;

/** Domain/auth failures; mapped by {@link GlobalExceptionHandler}. */
public class AuthException extends RuntimeException {

    private final boolean conflict;

    public AuthException(String message) {
        this(message, false);
    }

    private AuthException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public static AuthException conflict(String message) {
        return new AuthException(message, true);
    }

    public boolean isConflict() {
        return conflict;
    }
}
