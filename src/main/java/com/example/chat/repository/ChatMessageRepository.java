package com.example.chat.repository;

import com.example.chat.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Direct messages between two users, oldest first. */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE (m.fromUser = :a AND m.toUser = :b)
               OR (m.fromUser = :b AND m.toUser = :a)
            ORDER BY m.timestamp ASC
            """)
    List<ChatMessage> findConversation(@Param("a") String a, @Param("b") String b);

    /** Distinct usernames this user has ever DMed with (excludes group messages). */
    @Query("""
            SELECT DISTINCT CASE
                WHEN m.fromUser = :user THEN m.toUser
                ELSE m.fromUser
            END
            FROM ChatMessage m
            WHERE m.groupId IS NULL
              AND ((m.fromUser = :user AND m.toUser IS NOT NULL)
                   OR m.toUser = :user)
            """)
    List<String> findContacts(@Param("user") String user);

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.groupId = :groupId
            ORDER BY m.timestamp ASC
            """)
    List<ChatMessage> findByGroupIdOrderByTimestampAsc(@Param("groupId") Long groupId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChatMessage m SET m.fromUser = :newUsername WHERE LOWER(m.fromUser) = LOWER(:oldUsername)")
    int renameFromUser(@Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ChatMessage m SET m.toUser = :newUsername
            WHERE m.toUser IS NOT NULL AND LOWER(m.toUser) = LOWER(:oldUsername)
            """)
    int renameToUser(@Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
