package com.example.chat.service;

import com.example.chat.domain.MessageType;
import com.example.chat.dto.chat.SendGroupMessageCommand;
import com.example.chat.dto.group.CreateGroupRequest;
import com.example.chat.dto.group.GroupMemberResponse;
import com.example.chat.dto.group.GroupResponse;
import com.example.chat.exception.AuthException;
import com.example.chat.model.ChatGroup;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.GroupMember;
import com.example.chat.repository.ChatGroupRepository;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.GroupMemberRepository;
import com.example.chat.util.Usernames;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GroupService {

    public static final int MAX_GROUP_NAME_LENGTH = 80;
    public static final int MAX_MEMBERS = 50;

    private static final String USER_QUEUE = "/queue/messages";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MEMBER = "MEMBER";

    private final ChatGroupRepository groups;
    private final GroupMemberRepository members;
    private final ChatMessageRepository messages;
    private final SimpMessagingTemplate messagingTemplate;

    public GroupService(
            ChatGroupRepository groups,
            GroupMemberRepository members,
            ChatMessageRepository messages,
            SimpMessagingTemplate messagingTemplate) {
        this.groups = groups;
        this.members = members;
        this.messages = messages;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public GroupResponse create(String creatorUsername, CreateGroupRequest request) {
        String creator = Usernames.normalize(creatorUsername);
        if (creator.isEmpty()) {
            throw new AuthException("Not authenticated");
        }
        String name = request == null ? "" : request.nameOrEmpty();
        if (name.isEmpty()) {
            throw new AuthException("Group name is required");
        }
        if (name.length() > MAX_GROUP_NAME_LENGTH) {
            throw new AuthException("Group name is too long");
        }

        Set<String> memberNames = new LinkedHashSet<>();
        memberNames.add(creator);
        if (request != null) {
            for (String raw : request.membersOrEmpty()) {
                String n = Usernames.normalize(raw);
                if (!n.isEmpty() && !n.equalsIgnoreCase(creator)) {
                    memberNames.add(n);
                }
            }
        }
        if (memberNames.size() > MAX_MEMBERS) {
            throw new AuthException("Group cannot have more than " + MAX_MEMBERS + " members");
        }

        ChatGroup group = groups.save(new ChatGroup(name, creator));
        for (String username : memberNames) {
            String role = username.equalsIgnoreCase(creator) ? ROLE_ADMIN : ROLE_MEMBER;
            members.save(new GroupMember(group.getId(), username, role));
        }
        return GroupResponse.from(group, memberNames.size());
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listForUser(String username) {
        String user = Usernames.normalize(username);
        return groups.findGroupsForUser(user).stream()
                .map(g -> GroupResponse.from(g, members.findByGroupId(g.getId()).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> listMembers(String username, Long groupId) {
        requireMember(username, groupId);
        return members.findByGroupIdOrderByJoinedAtAsc(groupId).stream()
                .map(GroupMemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> history(String username, Long groupId) {
        requireMember(username, groupId);
        return messages.findByGroupIdOrderByTimestampAsc(groupId);
    }

    @Transactional
    public ChatMessage send(SendGroupMessageCommand command) {
        if (command == null || command.groupId() == null) {
            return null;
        }
        String from = Usernames.normalize(command.fromUser());
        requireMember(from, command.groupId());

        boolean hasMedia = command.mediaUrl() != null && !command.mediaUrl().isBlank();
        String body = command.text() == null ? "" : command.text().trim();
        if (!hasMedia && body.isEmpty()) {
            return null;
        }

        MessageType type = hasMedia
                ? MessageType.fromMedia(command.messageType(), command.mediaContentType())
                : MessageType.TEXT;

        ChatMessage message = messages.save(ChatMessage.createGroup(
                from,
                command.groupId(),
                body,
                type.name(),
                hasMedia ? command.mediaUrl().trim() : null,
                hasMedia ? command.mediaContentType() : null,
                hasMedia ? command.mediaFileName() : null));

        fanOut(message);
        return message;
    }

    private void fanOut(ChatMessage message) {
        Long groupId = message.getGroupId();
        if (groupId == null) {
            return;
        }
        for (GroupMember member : members.findByGroupId(groupId)) {
            messagingTemplate.convertAndSendToUser(member.getUsername(), USER_QUEUE, message);
        }
    }

    private void requireMember(String username, Long groupId) {
        String user = Usernames.normalize(username);
        if (user.isEmpty()) {
            throw new AuthException("Not authenticated");
        }
        if (groupId == null || !groups.existsById(groupId)) {
            throw new AuthException("Group not found");
        }
        if (!members.existsByGroupIdAndUsernameIgnoreCase(groupId, user)) {
            // Avoid leaking membership; same message as missing group for outsiders
            throw new AuthException("Group not found");
        }
    }
}
