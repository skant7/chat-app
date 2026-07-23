package com.example.chat.service;

import com.example.chat.dto.chat.ConversationPage;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceConversationTest {

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
    void firstPage_returnsNewestLimit_oldestFirstInBody_andCursorForOlder() {
        // Repo returns newest-first; service asks for limit+1 to detect hasMore.
        List<ChatMessage> newestFirst = messagesDesc(1, 51); // ids 51..1
        when(repository.findConversationNewest(eq("Alice"), eq("Bob"), any(Pageable.class)))
                .thenReturn(newestFirst);

        ConversationPage page = chatService.conversation("Alice", "Bob", null, 50);

        assertEquals(50, page.messages().size());
        // Body is oldest → newest within the page (ids 2..51 after dropping the +1 and reversing).
        assertEquals(2L, page.messages().get(0).getId());
        assertEquals(51L, page.messages().get(49).getId());
        assertTrue(page.hasMore());
        assertEquals(2L, page.nextBeforeId());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findConversationNewest(eq("Alice"), eq("Bob"), captor.capture());
        assertEquals(51, captor.getValue().getPageSize());
        verify(repository, never()).findConversationBefore(any(), any(), any(), any());
    }

    @Test
    void olderPage_usesExclusiveBeforeId_andNeverOverlapsPriorPageIds() {
        // First page ended with nextBeforeId = 51 (oldest of newest-50).
        // Older page: ids 50..1 (newest-first from repo for id < 51).
        when(repository.findConversationBefore(eq("Alice"), eq("Bob"), eq(51L), any(Pageable.class)))
                .thenReturn(messagesDesc(1, 50));

        ConversationPage older = chatService.conversation("Alice", "Bob", 51L, 50);

        assertEquals(50, older.messages().size());
        assertEquals(1L, older.messages().get(0).getId());
        assertEquals(50L, older.messages().get(49).getId());
        assertFalse(older.hasMore());
        assertNull(older.nextBeforeId());

        // No id >= 51 appears (exclusive cursor).
        for (ChatMessage m : older.messages()) {
            assertTrue(m.getId() < 51L);
        }
    }

    @Test
    void pagingWholeConversation_eachMessageOnce() {
        // 120 messages id 1..120; page size 50.
        // Page 1 (newest): repo returns 120..70 (51 rows) → body 71..120, nextBeforeId=71
        when(repository.findConversationNewest(eq("A"), eq("B"), any(Pageable.class)))
                .thenReturn(messagesDesc(70, 120));

        ConversationPage p1 = chatService.conversation("A", "B", null, 50);
        assertEquals(50, p1.messages().size());
        assertTrue(p1.hasMore());
        assertEquals(71L, p1.nextBeforeId());

        // Page 2: id < 71 → 70..20 (51 rows) → body 21..70, nextBeforeId=21
        when(repository.findConversationBefore(eq("A"), eq("B"), eq(71L), any(Pageable.class)))
                .thenReturn(messagesDesc(20, 70));

        ConversationPage p2 = chatService.conversation("A", "B", p1.nextBeforeId(), 50);
        assertEquals(50, p2.messages().size());
        assertTrue(p2.hasMore());
        assertEquals(21L, p2.nextBeforeId());

        // Page 3: id < 21 → 20..1 (20 rows) → body 1..20, no more
        when(repository.findConversationBefore(eq("A"), eq("B"), eq(21L), any(Pageable.class)))
                .thenReturn(messagesDesc(1, 20));

        ConversationPage p3 = chatService.conversation("A", "B", p2.nextBeforeId(), 50);
        assertEquals(20, p3.messages().size());
        assertFalse(p3.hasMore());
        assertNull(p3.nextBeforeId());

        Set<Long> seen = new HashSet<>();
        int total = 0;
        for (ConversationPage page : List.of(p1, p2, p3)) {
            for (ChatMessage m : page.messages()) {
                assertTrue(seen.add(m.getId()), "duplicate id " + m.getId());
                total++;
            }
        }
        assertEquals(120, total);
    }

    @Test
    void emptyOrSelfConversation_returnsEmptyPage() {
        assertEquals(ConversationPage.empty(), chatService.conversation("Alice", "Alice", null, 50));
        assertEquals(ConversationPage.empty(), chatService.conversation("", "Bob", null, 50));
        verify(repository, never()).findConversationNewest(any(), any(), any());
    }

    @Test
    void defaultPageSize_whenLimitMissingOrInvalid() {
        when(repository.findConversationNewest(eq("A"), eq("B"), any(Pageable.class)))
                .thenReturn(List.of());

        chatService.conversation("A", "B", null, null);
        chatService.conversation("A", "B", null, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, org.mockito.Mockito.times(2))
                .findConversationNewest(eq("A"), eq("B"), captor.capture());
        for (Pageable p : captor.getAllValues()) {
            // limit+1 for hasMore probe
            assertEquals(ChatService.DEFAULT_CONVERSATION_PAGE_SIZE + 1, p.getPageSize());
        }
    }

    /** Messages with ids fromLo..fromHi inclusive, newest-first (DESC). */
    private static List<ChatMessage> messagesDesc(long fromLo, long fromHi) {
        List<ChatMessage> list = new ArrayList<>();
        for (long id = fromHi; id >= fromLo; id--) {
            ChatMessage m = ChatMessage.create("Alice", "Bob", "m" + id, "TEXT", null, null, null, "c-" + id);
            m.setId(id);
            m.setTimestamp(id);
            list.add(m);
        }
        return list;
    }
}
