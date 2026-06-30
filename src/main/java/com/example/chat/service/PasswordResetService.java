package com.example.chat.service;

import com.example.chat.dto.auth.ForgotPasswordResponse;
import com.example.chat.exception.AuthException;
import com.example.chat.model.PasswordResetToken;
import com.example.chat.model.UserAccount;
import com.example.chat.repository.PasswordResetTokenRepository;
import com.example.chat.repository.UserAccountRepository;
import com.example.chat.util.Passwords;
import com.example.chat.util.TokenGenerator;
import com.example.chat.util.Usernames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Forgot / reset password flows (SRP). */
@Service
public class PasswordResetService {

    public static final long RESET_TOKEN_TTL_MS = 60L * 60 * 1000;

    private static final String GENERIC_MESSAGE =
            "If that account exists, a password reset token was issued.";

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final boolean returnResetToken;

    public PasswordResetService(
            UserAccountRepository users,
            PasswordResetTokenRepository resetTokens,
            SessionService sessionService,
            PasswordEncoder passwordEncoder,
            TokenGenerator tokenGenerator,
            @Value("${chat.auth.return-reset-token:true}") boolean returnResetToken) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.returnResetToken = returnResetToken;
    }

    @Transactional
    public ForgotPasswordResponse requestReset(String username) {
        String name = Usernames.normalize(username);
        if (name.isEmpty()) {
            throw new AuthException("Username is required");
        }

        Optional<UserAccount> account = users.findByUsernameIgnoreCase(name);
        if (account.isEmpty()) {
            return new ForgotPasswordResponse(GENERIC_MESSAGE, null);
        }

        resetTokens.deleteExpiredOrUsed(System.currentTimeMillis());
        resetTokens.deleteByUsername(account.get().getUsername());

        String token = tokenGenerator.nextToken();
        resetTokens.save(new PasswordResetToken(
                token, account.get().getUsername(), System.currentTimeMillis() + RESET_TOKEN_TTL_MS));

        return new ForgotPasswordResponse(GENERIC_MESSAGE, returnResetToken ? token : null);
    }

    @Transactional
    public void resetWithToken(String resetToken, String newPassword) {
        if (resetToken == null || resetToken.isBlank()) {
            throw new AuthException("Reset token is required");
        }
        Passwords.requireValid(newPassword);

        PasswordResetToken record = resetTokens.findByToken(resetToken.trim())
                .orElseThrow(() -> new AuthException("Invalid or expired reset token"));

        if (record.isUsed() || record.getExpiresAt() <= System.currentTimeMillis()) {
            throw new AuthException("Invalid or expired reset token");
        }

        UserAccount account = users.findByUsernameIgnoreCase(record.getUsername())
                .orElseThrow(() -> new AuthException("Invalid or expired reset token"));

        applyNewPassword(account, newPassword);
        record.setUsed(true);
        resetTokens.save(record);
        resetTokens.deleteByUsername(account.getUsername());
        sessionService.revokeAllForUser(account.getUsername());
    }

    private void applyNewPassword(UserAccount account, String newPassword) {
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(account);
    }
}
