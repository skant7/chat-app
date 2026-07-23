package com.example.chat.config;

import com.example.chat.service.ChatService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

/**
 * When a client subscribes to its private message queue after (re)connect, push any
 * messages that were persisted while they were offline (status still SENT).
 * <p>
 * Fires on SUBSCRIBE rather than CONNECT so the subscription is registered before
 * {@code convertAndSendToUser} — otherwise the simple broker would drop the frames.
 */
@Component
public class MessageCatchupListener {

    private final ChatService chatService;

    public MessageCatchupListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        Principal user = event.getUser();
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            return;
        }
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (!isPrivateMessageQueue(destination)) {
            return;
        }
        chatService.deliverPendingForUser(user.getName());
    }

    /** Client SUBSCRIBE destination is {@code /user/queue/messages}. */
    static boolean isPrivateMessageQueue(String destination) {
        if (destination == null || destination.isBlank()) {
            return false;
        }
        // Tolerate trailing slash or broker-rewritten forms ending in /queue/messages.
        String d = destination.trim();
        return d.equals("/user/queue/messages")
                || d.endsWith("/queue/messages");
    }
}
