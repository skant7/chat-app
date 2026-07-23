package com.example.chat.controller;

import com.example.chat.dto.chat.ContactSummary;
import com.example.chat.dto.chat.ConversationPage;
import com.example.chat.dto.chat.SendMessageCommand;
import com.example.chat.service.ChatService;
import com.example.chat.service.PresenceService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class ChatController {

    private final ChatService chatService;
    private final PresenceService presenceService;

    public ChatController(ChatService chatService, PresenceService presenceService) {
        this.chatService = chatService;
        this.presenceService = presenceService;
    }

    /**
     * Private DM. Clients send to /app/chat with { to, text, clientMessageId }
     * and optional media fields. Sender is the authenticated STOMP principal.
     * Retries with the same {@code clientMessageId} do not create another row.
     * <p>
     * If the recipient is offline, the row is still stored; when they reconnect and
     * subscribe to {@code /user/queue/messages}, pending SENT messages are pushed
     * automatically (see {@link com.example.chat.config.MessageCatchupListener}
     * and {@code /app/catchup}).
     */
    @MessageMapping("/chat")
    public void chat(@Payload Map<String, String> payload, Principal principal) {
        if (principal == null) {
            return;
        }
        chatService.send(SendMessageCommand.fromPayload(principal.getName(), payload));
    }

    /**
     * Explicit offline catch-up: client calls this right after subscribing to
     * {@code /user/queue/messages} so any SENT messages received while offline
     * are pushed without a page reload. Safe to call more than once.
     */
    @MessageMapping("/catchup")
    public void catchup(Principal principal) {
        if (principal == null) {
            return;
        }
        chatService.deliverPendingForUser(principal.getName());
    }

    /**
     * Recipient reports delivery/read: { messageId, status: DELIVERED|READ }
     * or bulk { messageIds: "1,2,3", status: READ }.
     */
    @MessageMapping("/chat/status")
    public void chatStatus(@Payload Map<String, String> payload, Principal principal) {
        if (principal == null || payload == null) {
            return;
        }
        String status = payload.get("status");
        String bulk = payload.get("messageIds");
        if (bulk != null && !bulk.isBlank()) {
            List<Long> ids = new java.util.ArrayList<>();
            for (String part : bulk.split(",")) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                try {
                    ids.add(Long.parseLong(t));
                } catch (NumberFormatException ignored) {
                    // skip bad tokens
                }
            }
            chatService.updateStatuses(principal.getName(), ids, status);
            return;
        }
        String idRaw = payload.get("messageId");
        if (idRaw == null || idRaw.isBlank()) {
            return;
        }
        try {
            chatService.updateStatus(principal.getName(), Long.parseLong(idRaw.trim()), status);
        } catch (NumberFormatException ignored) {
            // ignore
        }
    }

    /**
     * Conversation history with keyset pagination (newest page first, default size 50).
     * <p>
     * First page: {@code GET /api/conversation?me=&peer=}<br>
     * Older page: {@code GET /api/conversation?me=&peer=&beforeId=<nextBeforeId from prior page>}
     * <p>
     * Response: {@code { messages, nextBeforeId, hasMore }}. Messages within a page are
     * oldest → newest. Walking with {@code nextBeforeId} never returns a message twice.
     */
    @GetMapping("/api/conversation")
    public ConversationPage conversation(
            @RequestParam String me,
            @RequestParam String peer,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        return chatService.conversation(me, peer, beforeId, limit);
    }

    /**
     * Sidebar contacts for {@code me}: history peers + currently online users,
     * each with {@code unreadCount} (inbound messages not yet READ).
     */
    @GetMapping("/api/contacts")
    public List<ContactSummary> contacts(@RequestParam String me) {
        return chatService.sidebarUsers(me);
    }

    @GetMapping("/api/online")
    public Set<String> online() {
        return presenceService.onlineUsers();
    }
}
