package com.example.chat.service;

import com.example.chat.dto.auth.AuthResponse;
import com.example.chat.exception.AuthException;
import com.example.chat.model.UserAccount;
import com.example.chat.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionService sessionService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, passwordEncoder, sessionService);
    }

    @Test
    void register_hashesPasswordAndReturnsToken() {
        when(users.existsByUsernameIgnoreCase("Alice")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("hash-secret1");
        when(users.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(sessionService.issue("Alice")).thenReturn("session-token");

        AuthResponse result = authService.register("Alice", "secret1");

        assertThat(result.username()).isEqualTo("Alice");
        assertThat(result.token()).isEqualTo("session-token");

        ArgumentCaptor<UserAccount> userCap = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(userCap.capture());
        assertThat(userCap.getValue().getPasswordHash()).isEqualTo("hash-secret1");
        verify(sessionService).issue("Alice");
    }

    @Test
    void register_rejectsDuplicateUsername() {
        when(users.existsByUsernameIgnoreCase("Alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("Alice", "secret1"))
                .isInstanceOf(AuthException.class)
                .hasMessage("User already exists")
                .satisfies(ex -> assertThat(((AuthException) ex).isConflict()).isTrue());

        verify(users, never()).save(any());
    }

    @Test
    void register_rejectsDuplicateUsernameIgnoringCase() {
        when(users.existsByUsernameIgnoreCase("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("alice", "secret1"))
                .isInstanceOf(AuthException.class)
                .hasMessage("User already exists");

        verify(users, never()).save(any());
    }

    @Test
    void register_rejectsShortPassword() {
        assertThatThrownBy(() -> authService.register("Alice", "12345"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("at least");

        verify(users, never()).save(any());
    }

    @Test
    void register_rejectsBlankUsername() {
        assertThatThrownBy(() -> authService.register("  ", "secret1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Username is required");
    }

    @Test
    void login_succeedsWithCorrectPassword() {
        UserAccount account = new UserAccount("Bob", "stored-hash");
        when(users.findByUsernameIgnoreCase("Bob")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("correct", "stored-hash")).thenReturn(true);
        when(sessionService.issue("Bob")).thenReturn("tok");

        AuthResponse result = authService.login("Bob", "correct");

        assertThat(result.username()).isEqualTo("Bob");
        assertThat(result.token()).isEqualTo("tok");
    }

    @Test
    void login_failsWithWrongPassword() {
        UserAccount account = new UserAccount("Bob", "stored-hash");
        when(users.findByUsernameIgnoreCase("Bob")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("Bob", "wrong"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid username or password");

        verify(sessionService, never()).issue(any());
    }

    @Test
    void login_failsForUnknownUser() {
        when(users.findByUsernameIgnoreCase("Nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("Nobody", "secret1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void login_failsWithEmptyPassword() {
        assertThatThrownBy(() -> authService.login("Alice", ""))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void resolveUsername_delegatesToSessionService() {
        when(sessionService.resolveUsername("tok123")).thenReturn(Optional.of("Alice"));
        assertThat(authService.resolveUsername("tok123")).contains("Alice");
    }

    @Test
    void logout_delegatesToSessionService() {
        authService.logout("tok123");
        verify(sessionService).revokeToken("tok123");
    }

    @Test
    void login_isCaseInsensitiveOnUsernameLookup() {
        UserAccount account = new UserAccount("Alice", "hash");
        when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret1", "hash")).thenReturn(true);
        when(sessionService.issue("Alice")).thenReturn("t");

        AuthResponse result = authService.login("alice", "secret1");
        assertThat(result.username()).isEqualTo("Alice");
    }

    @Test
    void changePassword_requiresCurrentPassword() {
        when(sessionService.resolveUsername("sess")).thenReturn(Optional.of("Alice"));
        UserAccount account = new UserAccount("Alice", "old-hash");
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword("sess", "wrong", "newpass1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void changePassword_succeedsAndRevokesSessions() {
        when(sessionService.resolveUsername("sess")).thenReturn(Optional.of("Alice"));
        UserAccount account = new UserAccount("Alice", "old-hash");
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("oldpass1", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpass1")).thenReturn("new-hash");
        when(users.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.changePassword("sess", "oldpass1", "newpass1");

        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        verify(sessionService).revokeAllForUser("Alice");
    }
}
