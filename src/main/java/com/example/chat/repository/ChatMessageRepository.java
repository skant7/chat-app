package com.example.chat.repository;

import com.example.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Lookup by sender + client-generated id (idempotent send / retry). */
    Optional<ChatMessage> findByFromUserAndClientMessageId(String fromUser, String clientMessageId);

    /**
     * Newest messages between two users (no cursor). Ordered by id DESC.
     * Callers request {@code limit + 1} rows to detect whether an older page exists.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE (m.fromUser = :a AND m.toUser = :b)
               OR (m.fromUser = :b AND m.toUser = :a)
            ORDER BY m.id DESC
            """)
    List<ChatMessage> findConversationNewest(
            @Param("a") String a,
            @Param("b") String b,
            Pageable pageable);

    /**
     * Older messages strictly before {@code beforeId} (exclusive keyset cursor).
     * Guarantees no overlap with a prior page that ended at {@code beforeId}.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE ((m.fromUser = :a AND m.toUser = :b)
                OR (m.fromUser = :b AND m.toUser = :a))
              AND m.id < :beforeId
            ORDER BY m.id DESC
            """)
    List<ChatMessage> findConversationBefore(
            @Param("a") String a,
            @Param("b") String b,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    /** Distinct usernames this user has ever messaged with. */
    @Query("""
            SELECT DISTINCT CASE
                WHEN m.fromUser = :user THEN m.toUser
                ELSE m.fromUser
            END
            FROM ChatMessage m
            WHERE m.fromUser = :user OR m.toUser = :user
            """)
    List<String> findContacts(@Param("user") String user);

    /**
     * Unread inbound messages for {@code user}, grouped by sender.
     * Each row is {@code [fromUser, count]} where status is not READ.
     */
    @Query("""
            SELECT m.fromUser, COUNT(m)
            FROM ChatMessage m
            WHERE m.toUser = :user
              AND m.status <> 'READ'
            GROUP BY m.fromUser
            """)
    List<Object[]> countUnreadBySender(@Param("user") String user);

    /**
     * Inbound messages not yet received by the recipient's client (offline while sent).
     * Oldest first so catch-up arrives in conversation order.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.toUser = :user
              AND m.status = 'SENT'
            ORDER BY m.id ASC
            """)
    List<ChatMessage> findPendingDelivery(@Param("user") String user, Pageable pageable);
}
