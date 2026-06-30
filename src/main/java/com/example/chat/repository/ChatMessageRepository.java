package com.example.chat.repository;

import com.example.chat.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
