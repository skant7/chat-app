package com.example.chat.service;

import com.example.chat.model.ChatMessage;
import com.example.chat.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceCatchupTest {

    @Mock
    private ChatMessageRepository repository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PresenceService presenceService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(repository, messagingTemplate, presenceService);
    }

    @Test
    void deliverPendingForUser_pushesSentMessagesOldestFirst_toRecipientOnly() {
        ChatMessage older = ChatMessage.create("alice", "bob", "hi", "TEXT", null, null, null, "c-1");
        older.setId(10L);
        older.setStatus("SENT");
        ChatMessage newer = ChatMessage.create("alice", "bob", "hi2", "TEXT", null, null, null, "c-2");
        newer.setId(11L);
        newer.setStatus("SENT");

        when(repository.findPendingDelivery(eq("bob"), any(Pageable.class)))
                .thenReturn(List.of(older, newer));

        int n = chatService.deliverPendingForUser("bob");
        assertEquals(2, n);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2)).convertAndSendToUser(
                eq("bob"), eq("/queue/messages"), payload.capture());
        List<Object> sent = payload.getAllValues();
        assertEquals(10L, ((ChatMessage) sent.get(0)).getId());
        assertEquals(11L, ((ChatMessage) sent.get(1)).getId());
        // Sender is not re-notified on catch-up.
        verify(messagingTemplate, never()).convertAndSendToUser(eq("alice"), any(), any());
    }

    @Test
    void deliverPendingForUser_emptyName_noOp() {
        assertEquals(0, chatService.deliverPendingForUser("  "));
        verify(repository, never()).findPendingDelivery(any(), any());
    }
}
