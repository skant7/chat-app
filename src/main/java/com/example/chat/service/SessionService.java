package com.example.chat.service;

import com.example.chat.model.AuthSession;
import com.example.chat.repository.AuthSessionRepository;
import com.example.chat.util.TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Session lifecycle only (SRP). */
@Service
public class SessionService {

    public static final long SESSION_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    private final AuthSessionRepository sessions;
    private final TokenGenerator tokenGenerator;

    public SessionService(AuthSessionRepository sessions, TokenGenerator tokenGenerator) {
        this.sessions = sessions;
        this.tokenGenerator = tokenGenerator;
    }

    @Transactional
    public String issue(String username) {
        sessions.deleteExpired(System.currentTimeMillis());
        String token = tokenGenerator.nextToken();
        sessions.save(new AuthSession(token, username, System.currentTimeMillis() + SESSION_TTL_MS));
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveUsername(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessions.findByToken(token.trim())
                .filter(s -> s.getExpiresAt() > System.currentTimeMillis())
                .map(AuthSession::getUsername);
    }

    @Transactional
    public void revokeToken(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteByToken(token.trim());
        }
    }

    @Transactional
    public void revokeAllForUser(String username) {
        sessions.deleteByUsername(username);
    }
}
