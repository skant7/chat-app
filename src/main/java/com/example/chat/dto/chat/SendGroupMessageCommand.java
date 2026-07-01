package com.example.chat.dto.chat;

import java.util.Map;

/** STOMP payload for /app/group.chat. */
public record SendGroupMessageCommand(
        String fromUser,
        Long groupId,
        String text,
        String messageType,
        String mediaUrl,
        String mediaContentType,
        String mediaFileName) {

    public static SendGroupMessageCommand fromPayload(String fromUser, Map<String, Object> payload) {
        if (payload == null) {
            payload = Map.of();
        }
        return new SendGroupMessageCommand(
                fromUser,
                toLong(payload.get("groupId")),
                stringVal(payload.get("text")),
                stringVal(payload.get("messageType")),
                stringVal(payload.get("mediaUrl")),
                stringVal(payload.get("mediaContentType")),
                stringVal(payload.get("mediaFileName")));
    }

    private static String stringVal(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
