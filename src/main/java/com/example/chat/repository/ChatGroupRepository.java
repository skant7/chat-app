package com.example.chat.repository;

import com.example.chat.model.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {

    @Query("""
            SELECT g FROM ChatGroup g
            WHERE g.id IN (
                SELECT m.groupId FROM GroupMember m
                WHERE LOWER(m.username) = LOWER(:username)
            )
            ORDER BY g.name ASC
            """)
    List<ChatGroup> findGroupsForUser(@Param("username") String username);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChatGroup g SET g.createdBy = :newUsername WHERE LOWER(g.createdBy) = LOWER(:oldUsername)")
    int renameCreatedBy(@Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
