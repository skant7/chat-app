package com.example.chat.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/** Secure opaque tokens for sessions and password resets (SRP / DRY). */
@Component
public class TokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String nextToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
