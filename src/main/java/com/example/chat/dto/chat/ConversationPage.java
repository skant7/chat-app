package com.example.chat.dto.chat;

import com.example.chat.model.ChatMessage;

import java.util.List;

/**
 * One page of a direct conversation (keyset / cursor pagination).
 * <p>
 * Messages are ordered oldest → newest within the page (DOM-friendly).
 * The first request (no {@code beforeId}) returns the newest {@code limit} messages.
 * Pass {@link #nextBeforeId()} as {@code beforeId} to load the next older page.
 * Every message id appears in at most one page when walking with that cursor.
 */
public record ConversationPage(
        List<ChatMessage> messages,
        /** Pass as {@code beforeId} for the next older page; null when no older messages remain. */
        Long nextBeforeId,
        boolean hasMore
) {
    public static ConversationPage empty() {
        return new ConversationPage(List.of(), null, false);
    }
}
