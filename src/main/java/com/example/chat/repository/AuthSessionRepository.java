package com.example.chat.repository;

import com.example.chat.model.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByToken(String token);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AuthSession s WHERE s.token = :token")
    void deleteByToken(@Param("token") String token);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AuthSession s WHERE s.username = :username")
    void deleteByUsername(@Param("username") String username);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AuthSession s WHERE s.expiresAt < :now")
    void deleteExpired(@Param("now") long now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AuthSession s SET s.username = :newUsername WHERE LOWER(s.username) = LOWER(:oldUsername)")
    int updateUsername(@Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
