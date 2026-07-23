package com.example.chat.service;

import com.example.chat.dto.chat.ContactSummary;
import com.example.chat.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceUnreadTest {

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
    void unreadCountsBySender_mapsRows() {
        when(repository.countUnreadBySender(eq("alice"))).thenReturn(List.of(
                new Object[]{"bob", 3L},
                new Object[]{"carol", 1L}
        ));

        Map<String, Long> counts = chatService.unreadCountsBySender("alice");
        assertEquals(3L, counts.get("bob"));
        assertEquals(1L, counts.get("carol"));
        assertEquals(2, counts.size());
    }

    @Test
    void sidebarUsers_mergesContactsOnlineAndUnread() {
        when(repository.findContacts(eq("alice"))).thenReturn(List.of("bob"));
        when(repository.countUnreadBySender(eq("alice"))).thenReturn(List.of(
                new Object[]{"bob", 2L},
                new Object[]{"dave", 5L} // unread-only peer
        ));
        when(presenceService.onlineUsers()).thenReturn(Set.of("carol", "alice"));

        List<ContactSummary> rows = chatService.sidebarUsers("alice");
        Map<String, Long> byName = rows.stream()
                .collect(java.util.stream.Collectors.toMap(ContactSummary::username, ContactSummary::unreadCount));

        assertEquals(2L, byName.get("bob"));
        assertEquals(0L, byName.get("carol")); // online, no unread
        assertEquals(5L, byName.get("dave")); // unread-only included
        assertTrue(rows.stream().noneMatch(c -> c.username().equalsIgnoreCase("alice")));
    }
}
