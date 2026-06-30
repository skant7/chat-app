package com.example.chat.domain;

/** Chat message kinds persisted as strings for JPA simplicity. */
public enum MessageType {
    TEXT,
    IMAGE,
    FILE;

    public static MessageType fromMedia(String preferred, String contentType) {
        if (preferred != null && !preferred.isBlank()) {
            try {
                MessageType t = MessageType.valueOf(preferred.trim().toUpperCase());
                if (t != TEXT) {
                    return t;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return forContentType(contentType);
    }

    public static MessageType forContentType(String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return IMAGE;
        }
        return FILE;
    }
}
