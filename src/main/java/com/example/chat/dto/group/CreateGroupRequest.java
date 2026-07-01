package com.example.chat.dto.group;

import java.util.List;

public record CreateGroupRequest(String name, List<String> members) {

    public String nameOrEmpty() {
        return name == null ? "" : name.trim();
    }

    public List<String> membersOrEmpty() {
        return members == null ? List.of() : members;
    }
}
