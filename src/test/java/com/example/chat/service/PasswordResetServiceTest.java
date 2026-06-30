package com.example.chat.service;

import com.example.chat.dto.auth.ForgotPasswordResponse;
import com.example.chat.exception.AuthException;
import com.example.chat.model.PasswordResetToken;
import com.example.chat.model.UserAccount;
import com.example.chat.repository.PasswordResetTokenRepository;
import com.example.chat.repository.UserAccountRepository;
import com.example.chat.util.TokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class PasswordResetServiceTest {

    @Mock
    private UserAccountRepository users;
    @Mock
    private PasswordResetTokenRepository resetTokens;
    @Mock
    private SessionService sessionService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenGenerator tokenGenerator;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                users, resetTokens, sessionService, passwordEncoder, tokenGenerator, true);
    }

    @Test
    void requestReset_returnsTokenWhenUserExists() {
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(new UserAccount("Alice", "h")));
        when(tokenGenerator.nextToken()).thenReturn("reset-tok");
        when(resetTokens.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        ForgotPasswordResponse result = service.requestReset("Alice");

        assertThat(result.resetToken()).isEqualTo("reset-tok");
        verify(resetTokens).deleteByUsername("Alice");
    }

    @Test
    void requestReset_noTokenWhenUserMissing() {
        when(users.findByUsernameIgnoreCase("Ghost")).thenReturn(Optional.empty());

        ForgotPasswordResponse result = service.requestReset("Ghost");

        assertThat(result.resetToken()).isNull();
        verify(resetTokens, never()).save(any());
    }

    @Test
    void requestReset_hidesTokenWhenConfigured() {
        service = new PasswordResetService(
                users, resetTokens, sessionService, passwordEncoder, tokenGenerator, false);
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(new UserAccount("Alice", "h")));
        when(tokenGenerator.nextToken()).thenReturn("reset-tok");
        when(resetTokens.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.requestReset("Alice").resetToken()).isNull();
    }

    @Test
    void resetWithToken_updatesHashAndRevokesSessions() {
        PasswordResetToken reset = new PasswordResetToken("reset-tok", "Alice", System.currentTimeMillis() + 60_000);
        UserAccount account = new UserAccount("Alice", "old-hash");
        when(resetTokens.findByToken("reset-tok")).thenReturn(Optional.of(reset));
        when(users.findByUsernameIgnoreCase("Alice")).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("newpass1")).thenReturn("new-hash");
        when(users.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(resetTokens.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resetWithToken("reset-tok", "newpass1");

        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        verify(sessionService).revokeAllForUser("Alice");
        verify(resetTokens).deleteByUsername("Alice");
    }

    @Test
    void resetWithToken_rejectsExpired() {
        PasswordResetToken reset = new PasswordResetToken("old", "Alice", System.currentTimeMillis() - 1);
        when(resetTokens.findByToken("old")).thenReturn(Optional.of(reset));

        assertThatThrownBy(() -> service.resetWithToken("old", "newpass1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid or expired");
    }
}
