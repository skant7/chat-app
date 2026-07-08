package com.example.chat.repository;

import com.example.chat.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.username = :username")
    void deleteByUsername(@Param("username") String username);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now OR t.used = true")
    void deleteExpiredOrUsed(@Param("now") long now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.username = :newUsername WHERE LOWER(t.username) = LOWER(:oldUsername)")
    int updateUsername(@Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
