package com.example.chat.config;

import java.security.Principal;

/** STOMP user identity after token authentication. */
public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
