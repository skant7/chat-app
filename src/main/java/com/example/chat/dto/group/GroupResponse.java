package com.example.chat.dto.group;

import com.example.chat.model.ChatGroup;

public record GroupResponse(Long id, String name, String createdBy, long createdAt, int memberCount) {

    public static GroupResponse from(ChatGroup group, int memberCount) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getCreatedBy(),
                group.getCreatedAt(),
                memberCount);
    }
}
