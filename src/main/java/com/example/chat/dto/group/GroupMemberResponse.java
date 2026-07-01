package com.example.chat.dto.group;

import com.example.chat.model.GroupMember;

public record GroupMemberResponse(String username, String role, long joinedAt) {

    public static GroupMemberResponse from(GroupMember member) {
        return new GroupMemberResponse(member.getUsername(), member.getRole(), member.getJoinedAt());
    }
}
