package com.example.chat.service;

import com.example.chat.dto.group.CreateGroupRequest;
import com.example.chat.exception.AuthException;
import com.example.chat.model.ChatGroup;
import com.example.chat.model.GroupMember;
import com.example.chat.model.UserAccount;
import com.example.chat.repository.ChatGroupRepository;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.GroupMemberRepository;
import com.example.chat.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceCreateTest {

    @Mock
    private ChatGroupRepository groups;
    @Mock
    private GroupMemberRepository members;
    @Mock
    private ChatMessageRepository messages;
    @Mock
    private UserAccountRepository users;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groups, members, messages, users, messagingTemplate);
    }

    @Test
    void create_resolvesMembersToCanonicalUsernames() {
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account("Alice")));
        when(users.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(account("Bob")));
        when(groups.save(any(ChatGroup.class))).thenAnswer(inv -> {
            ChatGroup g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });
        when(members.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = groupService.create("Alice", new CreateGroupRequest("Team", List.of("bob")));

        assertThat(response.memberCount()).isEqualTo(2);
        ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
        verify(members, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(GroupMember::getUsername)
                .containsExactly("Alice", "Bob");
        assertThat(captor.getAllValues().get(0).getRole()).isEqualTo("ADMIN");
        assertThat(captor.getAllValues().get(1).getRole()).isEqualTo("MEMBER");
    }

    @Test
    void create_rejectsUnknownMembers() {
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account("Alice")));
        when(users.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                groupService.create("Alice", new CreateGroupRequest("Team", List.of("nobody"))))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Unknown user: nobody");

        verify(groups, never()).save(any());
        verify(members, never()).save(any());
    }

    @Test
    void create_dedupesMembersCaseInsensitively() {
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account("Alice")));
        when(users.findByUsernameIgnoreCase("Bob")).thenReturn(Optional.of(account("Bob")));
        when(users.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(account("Bob")));
        when(groups.save(any(ChatGroup.class))).thenAnswer(inv -> {
            ChatGroup g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });
        when(members.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = groupService.create(
                "Alice", new CreateGroupRequest("Team", List.of("Bob", "bob", "Alice")));

        assertThat(response.memberCount()).isEqualTo(2);
        ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
        verify(members, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(GroupMember::getUsername)
                .containsExactly("Alice", "Bob");
    }

    @Test
    void create_requiresAuthenticatedCreator() {
        when(users.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                groupService.create("ghost", new CreateGroupRequest("Team", List.of())))
                .isInstanceOf(AuthException.class)
                .hasMessage("Not authenticated");

        verify(groups, never()).save(any());
    }

    private static UserAccount account(String username) {
        return new UserAccount(username, "hash");
    }
}
