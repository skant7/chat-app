package com.example.chat.dto.chat;

import java.util.Map;

/** Immutable command for sending a DM (from STOMP payload or API). */
public record SendMessageCommand(
        String fromUser,
        String toUser,
        String text,
        String messageType,
        String mediaUrl,
        String mediaContentType,
        String mediaFileName,
        String clientMessageId) {

    public static SendMessageCommand fromPayload(String fromUser, Map<String, String> payload) {
        if (payload == null) {
            payload = Map.of();
        }
        return new SendMessageCommand(
                fromUser,
                payload.getOrDefault("to", ""),
                payload.getOrDefault("text", ""),
                payload.get("messageType"),
                payload.get("mediaUrl"),
                payload.get("mediaContentType"),
                payload.get("mediaFileName"),
                payload.get("clientMessageId"));
    }
}
