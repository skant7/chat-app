package com.example.chat.service;

import com.example.chat.domain.MessageStatus;
import com.example.chat.domain.MessageType;
import com.example.chat.dto.chat.ContactSummary;
import com.example.chat.dto.chat.ConversationPage;
import com.example.chat.dto.chat.SendMessageCommand;
import com.example.chat.model.ChatMessage;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.util.Usernames;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatService {

    private static final String USER_QUEUE = "/queue/messages";

    /** Default page size for conversation history (newest-first keyset pages). */
    public static final int DEFAULT_CONVERSATION_PAGE_SIZE = 50;
    private static final int MAX_CONVERSATION_PAGE_SIZE = 100;

    private final ChatMessageRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    public ChatService(
            ChatMessageRepository repository,
            SimpMessagingTemplate messagingTemplate,
            PresenceService presenceService) {
        this.repository = repository;
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
    }

    @Transactional
    public ChatMessage send(SendMessageCommand command) {
        if (command == null) {
            return null;
        }
        String from = Usernames.normalize(command.fromUser());
        String to = Usernames.normalize(command.toUser());
        if (from.isEmpty() || to.isEmpty() || from.equalsIgnoreCase(to)) {
            return null;
        }

        String clientMessageId = command.clientMessageId() == null
                ? ""
                : command.clientMessageId().trim();
        // Column is NOT NULL; generate a server fallback only when the client omits it.
        if (clientMessageId.isEmpty()) {
            clientMessageId = "server-" + UUID.randomUUID();
        }

        // Idempotent retry: same sender + clientMessageId reuses the existing row (no insert).
        var existing = repository.findByFromUserAndClientMessageId(from, clientMessageId);
        if (existing.isPresent()) {
            ChatMessage prior = existing.get();
            deliverToParticipants(prior);
            return prior;
        }

        boolean hasMedia = command.mediaUrl() != null && !command.mediaUrl().isBlank();
        String body = command.text() == null ? "" : command.text().trim();
        if (!hasMedia && body.isEmpty()) {
            return null;
        }

        MessageType type = hasMedia
                ? MessageType.fromMedia(command.messageType(), command.mediaContentType())
                : MessageType.TEXT;

        ChatMessage message = repository.save(ChatMessage.create(
                from,
                to,
                body,
                type.name(),
                hasMedia ? command.mediaUrl().trim() : null,
                hasMedia ? command.mediaContentType() : null,
                hasMedia ? command.mediaFileName() : null,
                clientMessageId));

        deliverToParticipants(message);
        return message;
    }

    /**
     * Recipient reports DELIVERED (frame received) or READ (message visible in open chat).
     * Only the recipient ({@code toUser}) may advance status; transitions are forward-only.
     * Updated message is pushed to both participants so the sender can update ticks.
     */
    @Transactional
    public ChatMessage updateStatus(String actor, Long messageId, String statusRaw) {
        if (actor == null || messageId == null) {
            return null;
        }
        String me = Usernames.normalize(actor);
        MessageStatus target = MessageStatus.parse(statusRaw);
        if (target == MessageStatus.SENT) {
            return null;
        }

        return repository.findById(messageId).map(message -> {
            if (!me.equalsIgnoreCase(message.getToUser())) {
                return null;
            }
            MessageStatus current = MessageStatus.parse(message.getStatus());
            if (!current.canAdvanceTo(target)) {
                // Already at or past this status — still re-push so sender UI can sync.
                deliverToParticipants(message);
                return message;
            }
            long now = System.currentTimeMillis();
            message.setStatus(target.name());
            if (target == MessageStatus.DELIVERED && message.getDeliveredAt() == null) {
                message.setDeliveredAt(now);
            }
            if (target == MessageStatus.READ) {
                if (message.getDeliveredAt() == null) {
                    message.setDeliveredAt(now);
                }
                message.setReadAt(now);
            }
            ChatMessage saved = repository.save(message);
            deliverToParticipants(saved);
            return saved;
        }).orElse(null);
    }

    /** Mark many messages READ (or DELIVERED) for the recipient in one request. */
    @Transactional
    public List<ChatMessage> updateStatuses(String actor, List<Long> messageIds, String statusRaw) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> updated = new ArrayList<>();
        for (Long id : messageIds) {
            ChatMessage m = updateStatus(actor, id, statusRaw);
            if (m != null) {
                updated.add(m);
            }
        }
        return updated;
    }

    /**
     * Cursor-paginated conversation history (newest page first).
     * <ul>
     *   <li>Omit {@code beforeId} for the newest {@code limit} messages.</li>
     *   <li>Pass {@code beforeId} = previous page's {@code nextBeforeId} for older messages.</li>
     *   <li>Keyset is exclusive on id ({@code id < beforeId}), so walking pages never
     *       returns the same message twice.</li>
     * </ul>
     * Returned {@link ConversationPage#messages()} are oldest → newest within the page.
     */
    @Transactional(readOnly = true)
    public ConversationPage conversation(String a, String b, Long beforeId, Integer limit) {
        String userA = Usernames.normalize(a);
        String userB = Usernames.normalize(b);
        if (userA.isEmpty() || userB.isEmpty() || userA.equalsIgnoreCase(userB)) {
            return ConversationPage.empty();
        }
        int pageSize = normalizePageSize(limit);
        // Fetch one extra row to know if an older page exists without a COUNT query.
        var pageable = PageRequest.of(0, pageSize + 1);
        List<ChatMessage> rows = beforeId == null
                ? repository.findConversationNewest(userA, userB, pageable)
                : repository.findConversationBefore(userA, userB, beforeId, pageable);

        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        // rows are id DESC (newest first); cursor for older page is the oldest id in this page.
        Long nextBeforeId = hasMore && !rows.isEmpty()
                ? rows.get(rows.size() - 1).getId()
                : null;
        // Flip to oldest → newest for top-to-bottom rendering.
        Collections.reverse(rows);
        return new ConversationPage(List.copyOf(rows), nextBeforeId, hasMore);
    }

    private static int normalizePageSize(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_CONVERSATION_PAGE_SIZE;
        }
        return Math.min(limit, MAX_CONVERSATION_PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public List<String> contacts(String user) {
        return repository.findContacts(Usernames.normalize(user));
    }

    /**
     * Unread inbound counts keyed by sender username
     * ({@code toUser = me} and {@code status <> READ}).
     */
    @Transactional(readOnly = true)
    public Map<String, Long> unreadCountsBySender(String me) {
        String self = Usernames.normalize(me);
        if (self.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : repository.countUnreadBySender(self)) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            String from = String.valueOf(row[0]);
            long count = row[1] instanceof Number n ? n.longValue() : 0L;
            if (count > 0) {
                counts.put(from, count);
            }
        }
        return counts;
    }

    /**
     * Sidebar rows: contacts from history plus anyone currently online (except self),
     * each with an unread inbound count for the signed-in user.
     */
    @Transactional(readOnly = true)
    public List<ContactSummary> sidebarUsers(String me) {
        String self = Usernames.normalize(me);
        Map<String, Long> unread = unreadCountsBySender(self);
        Set<String> names = new LinkedHashSet<>(contacts(self));
        for (String online : presenceService.onlineUsers()) {
            if (!online.equalsIgnoreCase(self)) {
                names.add(online);
            }
        }
        // Include any sender with unread even if not yet in contact/online sets.
        names.addAll(unread.keySet());

        List<ContactSummary> rows = new ArrayList<>(names.size());
        for (String name : names) {
            if (name == null || name.isBlank() || name.equalsIgnoreCase(self)) {
                continue;
            }
            rows.add(new ContactSummary(name, unread.getOrDefault(name, 0L)));
        }
        return List.copyOf(rows);
    }

    /**
     * Max undelivered (status=SENT) messages pushed in one catch-up after reconnect.
     * Prevents flooding the broker if a user was offline for a very long time.
     */
    public static final int MAX_CATCHUP_BATCH = 500;

    /**
     * Push every still-undelivered inbound message to {@code username}'s private queue.
     * <p>
     * Messages are always persisted on send; the simple STOMP broker does not store
     * offline frames. When the user reconnects and subscribes to {@code /user/queue/messages},
     * this replays {@code status = SENT} rows so the client receives them without a page reload.
     * The client then advances status to DELIVERED/READ via the existing receipt flow.
     *
     * @return number of messages pushed
     */
    @Transactional(readOnly = true)
    public int deliverPendingForUser(String username) {
        String user = Usernames.normalize(username);
        if (user.isEmpty()) {
            return 0;
        }
        List<ChatMessage> pending = repository.findPendingDelivery(
                user, PageRequest.of(0, MAX_CATCHUP_BATCH));
        for (ChatMessage message : pending) {
            // Only the recipient needs the offline catch-up frame; sender already has the row.
            messagingTemplate.convertAndSendToUser(user, USER_QUEUE, message);
        }
        return pending.size();
    }

    private void deliverToParticipants(ChatMessage message) {
        messagingTemplate.convertAndSendToUser(message.getFromUser(), USER_QUEUE, message);
        messagingTemplate.convertAndSendToUser(message.getToUser(), USER_QUEUE, message);
    }
}
