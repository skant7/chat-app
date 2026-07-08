package com.example.chat.dto.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** STOMP payload for /app/chat.ack — one id or a batch. */
public record DeliveryAckCommand(List<Long> messageIds) {

    @SuppressWarnings("unchecked")
    public static DeliveryAckCommand fromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return new DeliveryAckCommand(List.of());
        }
        List<Long> ids = new ArrayList<>();
        Object batch = payload.get("messageIds");
        if (batch instanceof List<?> list) {
            for (Object o : list) {
                Long id = toLong(o);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        Long single = toLong(payload.get("messageId"));
        if (single != null) {
            ids.add(single);
        }
        return new DeliveryAckCommand(List.copyOf(ids));
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
