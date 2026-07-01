package com.example.chat.controller;

import com.example.chat.dto.chat.DeliveryAckCommand;
import com.example.chat.dto.chat.SendGroupMessageCommand;
import com.example.chat.dto.chat.SendMessageCommand;
import com.example.chat.model.ChatMessage;
import com.example.chat.service.ChatService;
import com.example.chat.service.GroupService;
import com.example.chat.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Chat", description = "Conversation history, contacts, and presence (REST only; send via STOMP)")
public class ChatController {

    private final ChatService chatService;
    private final GroupService groupService;
    private final PresenceService presenceService;

    public ChatController(
            ChatService chatService,
            GroupService groupService,
            PresenceService presenceService) {
        this.chatService = chatService;
        this.groupService = groupService;
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
     * Group message. Clients send to /app/group.chat with { groupId, text } and optional media.
     * Fan-out to all members on /user/queue/messages.
     */
    @MessageMapping("/group.chat")
    public void groupChat(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) {
            return;
        }
        groupService.send(SendGroupMessageCommand.fromPayload(principal.getName(), payload));
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

    @Operation(summary = "DM history between me and peer")
    @GetMapping("/api/conversation")
    public List<ChatMessage> conversation(@RequestParam String me, @RequestParam String peer) {
        return chatService.conversation(me, peer);
    }

    @Operation(summary = "Sidebar contacts (history + online users)")
    @GetMapping("/api/contacts")
    public List<String> contacts(@RequestParam String me) {
        return chatService.sidebarUsers(me);
    }

    @Operation(summary = "Currently online usernames")
    @GetMapping("/api/online")
    public Set<String> online() {
        return presenceService.onlineUsers();
    }
}
