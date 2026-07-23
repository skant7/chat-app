package com.example.chat.domain;

/** Delivery lifecycle for a chat message (STOMP + UI ticks). */
public enum MessageStatus {
    SENT,
    DELIVERED,
    READ;

    public boolean canAdvanceTo(MessageStatus next) {
        return next != null && next.ordinal() > this.ordinal();
    }

    public static MessageStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SENT;
        }
        try {
            return MessageStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SENT;
        }
    }
}
