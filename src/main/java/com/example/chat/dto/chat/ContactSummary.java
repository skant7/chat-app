package com.example.chat.dto.chat;

/**
 * Sidebar contact: display name plus how many inbound messages are not yet READ.
 */
public record ContactSummary(String username, long unreadCount) {
}
