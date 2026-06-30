package com.example.chat.service;

import com.example.chat.dto.auth.AuthResponse;
import com.example.chat.exception.AuthException;
import com.example.chat.model.UserAccount;
import com.example.chat.repository.UserAccountRepository;
import com.example.chat.util.Passwords;
import com.example.chat.util.Usernames;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Registration, login, and authenticated password change.
 * Session and reset flows delegated to {@link SessionService} / {@link PasswordResetService} (SRP).
 */
@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    public AuthService(
            UserAccountRepository users,
            PasswordEncoder passwordEncoder,
            SessionService sessionService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    @Transactional
    public AuthResponse register(String username, String password) {
        String name = Usernames.normalize(username);
        Usernames.requireValid(name);
        Passwords.requireValid(password);

        if (users.existsByUsernameIgnoreCase(name)) {
            throw AuthException.conflict("User already exists");
        }

        try {
            UserAccount account = users.save(new UserAccount(name, passwordEncoder.encode(password)));
            return new AuthResponse(account.getUsername(), sessionService.issue(account.getUsername()));
        } catch (DataIntegrityViolationException e) {
            throw AuthException.conflict("User already exists");
        }
    }

    @Transactional
    public AuthResponse login(String username, String password) {
        String name = Usernames.normalize(username);
        if (name.isEmpty() || password == null || password.isEmpty()) {
            throw new AuthException("Invalid username or password");
        }

        UserAccount account = users.findByUsernameIgnoreCase(name)
                .orElseThrow(() -> new AuthException("Invalid username or password"));

        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new AuthException("Invalid username or password");
        }

        return new AuthResponse(account.getUsername(), sessionService.issue(account.getUsername()));
    }

    @Transactional
    public void changePassword(String sessionToken, String currentPassword, String newPassword) {
        String username = sessionService.resolveUsername(sessionToken)
                .orElseThrow(() -> new AuthException("Not authenticated"));

        Passwords.requireValid(newPassword);

        UserAccount account = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new AuthException("Not authenticated"));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new AuthException("Current password is incorrect");
        }

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(account);
        sessionService.revokeAllForUser(account.getUsername());
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveUsername(String token) {
        return sessionService.resolveUsername(token);
    }

    @Transactional
    public void logout(String token) {
        sessionService.revokeToken(token);
    }
}
