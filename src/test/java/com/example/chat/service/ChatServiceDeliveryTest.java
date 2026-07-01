package com.example.chat.service;

import com.example.chat.model.ChatMessage;
import com.example.chat.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceDeliveryTest {

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
    void acknowledgeDelivered_setsStatusWhenRecipientAcks() {
        ChatMessage msg = ChatMessage.create("Alice", "Bob", "hi", "TEXT", null, null, null);
        msg.setId(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(msg));
        when(repository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        List<ChatMessage> result = chatService.acknowledgeDelivered("Bob", List.of(10L));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getFirst().getDeliveredAt()).isNotNull();
        verify(messagingTemplate, times(2)).convertAndSendToUser(any(), eq("/queue/messages"), any());
    }

    @Test
    void acknowledgeDelivered_ignoresNonRecipient() {
        ChatMessage msg = ChatMessage.create("Alice", "Bob", "hi", "TEXT", null, null, null);
        msg.setId(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(msg));

        List<ChatMessage> result = chatService.acknowledgeDelivered("Alice", List.of(10L));

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void acknowledgeDelivered_isIdempotent() {
        ChatMessage msg = ChatMessage.create("Alice", "Bob", "hi", "TEXT", null, null, null);
        msg.setId(10L);
        msg.setStatus("DELIVERED");
        msg.setDeliveredAt(1L);
        when(repository.findById(10L)).thenReturn(Optional.of(msg));

        List<ChatMessage> result = chatService.acknowledgeDelivered("Bob", List.of(10L));

        assertThat(result).hasSize(1);
        verify(repository, never()).save(any());
    }
}
