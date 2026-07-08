package com.example.chat.repository;

import com.example.chat.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.Pk> {

    List<GroupMember> findByGroupIdOrderByJoinedAtAsc(Long groupId);

    boolean existsByGroupIdAndUsernameIgnoreCase(Long groupId, String username);

    List<GroupMember> findByGroupId(Long groupId);

    /** Native update: username is part of the composite primary key. */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE group_members
            SET username = :newUsername
            WHERE LOWER(username) = LOWER(:oldUsername)
            """, nativeQuery = true)
    int renameUsername(@Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
