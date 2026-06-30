package com.example.chat.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** DRY mapping of service exceptions to HTTP responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AuthException e) {
        HttpStatus status;
        if (e.isConflict()) {
            status = HttpStatus.CONFLICT;
        } else if (isUnauthorizedMessage(e.getMessage())) {
            status = HttpStatus.UNAUTHORIZED;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private static boolean isUnauthorizedMessage(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("Invalid username or password")
                || message.contains("Not authenticated")
                || message.contains("Current password is incorrect");
    }
}
