package com.example.chat.controller;

import com.example.chat.dto.chat.DeliveryAckCommand;
import com.example.chat.dto.chat.SendMessageCommand;
import com.example.chat.model.ChatMessage;
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
     * Private DM. Clients send to /app/chat with { to, text } and optional media fields.
     * Sender is taken from the authenticated STOMP principal.
     */
    @MessageMapping("/chat")
    public void chat(@Payload Map<String, String> payload, Principal principal) {
        if (principal == null) {
            return;
        }
        chatService.send(SendMessageCommand.fromPayload(principal.getName(), payload));
    }

    /**
     * Delivery receipt. Client sends to /app/chat.ack with { messageId } or { messageIds: [...] }.
     * Only the message recipient may acknowledge.
     */
    @MessageMapping("/chat.ack")
    public void acknowledgeDelivery(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) {
            return;
        }
        DeliveryAckCommand cmd = DeliveryAckCommand.fromPayload(payload);
        chatService.acknowledgeDelivered(principal.getName(), cmd.messageIds());
    }

    @GetMapping("/api/conversation")
    public List<ChatMessage> conversation(@RequestParam String me, @RequestParam String peer) {
        return chatService.conversation(me, peer);
    }

    @GetMapping("/api/contacts")
    public List<String> contacts(@RequestParam String me) {
        return chatService.sidebarUsers(me);
    }

    @GetMapping("/api/online")
    public Set<String> online() {
        return presenceService.onlineUsers();
    }
}
