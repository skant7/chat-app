package com.example.chat.repository;

import com.example.chat.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.Pk> {

    List<GroupMember> findByGroupIdOrderByJoinedAtAsc(Long groupId);

    boolean existsByGroupIdAndUsernameIgnoreCase(Long groupId, String username);

    List<GroupMember> findByGroupId(Long groupId);
}
