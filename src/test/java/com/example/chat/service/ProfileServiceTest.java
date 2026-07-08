package com.example.chat.service;

import com.example.chat.dto.profile.UpdateProfileRequest;
import com.example.chat.exception.AuthException;
import com.example.chat.model.UserAccount;
import com.example.chat.repository.AuthSessionRepository;
import com.example.chat.repository.ChatGroupRepository;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.GroupMemberRepository;
import com.example.chat.repository.PasswordResetTokenRepository;
import com.example.chat.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserAccountRepository users;
    @Mock
    private AuthSessionRepository sessions;
    @Mock
    private PasswordResetTokenRepository resetTokens;
    @Mock
    private ChatMessageRepository messages;
    @Mock
    private ChatGroupRepository groups;
    @Mock
    private GroupMemberRepository groupMembers;
    @Mock
    private PresenceService presenceService;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(
                users, sessions, resetTokens, messages, groups, groupMembers, presenceService);
    }

    @Test
    void getProfile_returnsUsernameAndAbout() {
        UserAccount account = new UserAccount("Alice", "hash");
        account.setAbout("Hello world");
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account));

        var profile = profileService.getProfile("Alice");

        assertThat(profile.username()).isEqualTo("Alice");
        assertThat(profile.about()).isEqualTo("Hello world");
    }

    @Test
    void updateProfile_updatesAboutWithoutRename() {
        UserAccount account = new UserAccount("Alice", "hash");
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account));
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        var profile = profileService.updateProfile(
                "Alice", new UpdateProfileRequest("Alice", "  About me  "));

        assertThat(profile.username()).isEqualTo("Alice");
        assertThat(profile.about()).isEqualTo("About me");
        verify(sessions, never()).updateUsername(any(), any());
        verify(presenceService, never()).rename(any(), any());
    }

    @Test
    void updateProfile_renamesAndCascades() {
        UserAccount account = new UserAccount("Alice", "hash");
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account));
        when(users.existsByUsernameIgnoreCase("Bob")).thenReturn(false);
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        var profile = profileService.updateProfile(
                "Alice", new UpdateProfileRequest("Bob", "renamed"));

        assertThat(profile.username()).isEqualTo("Bob");
        assertThat(profile.about()).isEqualTo("renamed");
        verify(users).saveAndFlush(account);
        verify(sessions).updateUsername("Alice", "Bob");
        verify(resetTokens).updateUsername("Alice", "Bob");
        verify(messages).renameFromUser("Alice", "Bob");
        verify(messages).renameToUser("Alice", "Bob");
        verify(groups).renameCreatedBy("Alice", "Bob");
        verify(groupMembers).renameUsername("Alice", "Bob");
        verify(presenceService).rename("Alice", "Bob");
    }

    @Test
    void updateProfile_rejectsTakenUsername() {
        UserAccount account = new UserAccount("Alice", "hash");
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account));
        when(users.existsByUsernameIgnoreCase("Bob")).thenReturn(true);

        assertThatThrownBy(() ->
                profileService.updateProfile("Alice", new UpdateProfileRequest("Bob", "")))
                .isInstanceOf(AuthException.class)
                .hasMessage("User already exists");

        verify(users, never()).saveAndFlush(any());
        verify(sessions, never()).updateUsername(any(), any());
    }

    @Test
    void updateProfile_rejectsAboutTooLong() {
        UserAccount account = new UserAccount("Alice", "hash");
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account));
        String longAbout = "x".repeat(ProfileService.MAX_ABOUT_LENGTH + 1);

        assertThatThrownBy(() ->
                profileService.updateProfile("Alice", new UpdateProfileRequest("Alice", longAbout)))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("About is too long");
    }

    @Test
    void updateProfile_allowsCasingOnlyRename() {
        UserAccount account = new UserAccount("Alice", "hash");
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account));
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        var profile = profileService.updateProfile(
                "Alice", new UpdateProfileRequest("alice", ""));

        assertThat(profile.username()).isEqualTo("alice");
        verify(users, never()).existsByUsernameIgnoreCase(eq("alice"));
        verify(sessions).updateUsername("Alice", "alice");
        verify(presenceService).rename("Alice", "alice");
    }
}
