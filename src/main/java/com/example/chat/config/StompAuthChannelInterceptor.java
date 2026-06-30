package com.example.chat.config;

import com.example.chat.service.SessionService;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/** Authenticates STOMP CONNECT using session tokens (SRP away from WebSocketConfig). */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final SessionService sessionService;

    public StompAuthChannelInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = extractToken(accessor);
        String username = sessionService.resolveUsername(token)
                .orElseThrow(() -> new IllegalArgumentException("Authentication required"));
        accessor.setUser(new StompPrincipal(username));
        return message;
    }

    private static String extractToken(StompHeaderAccessor accessor) {
        String token = firstNativeHeader(accessor, "token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        String authorization = firstNativeHeader(accessor, "Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return authorization;
    }

    private static String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        var values = accessor.getNativeHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
