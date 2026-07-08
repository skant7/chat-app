package com.example.chat.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String AUTH_TOKEN_SCHEME = "AuthToken";

    @Bean
    public OpenAPI chatOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Private Chat API")
                        .description("""
                                REST API for auth, profile, contacts, conversation history, groups, and media.
                                Real-time messaging uses STOMP over WebSocket at /ws (not shown here).
                                """.stripIndent().trim())
                        .version("1.0"))
                .components(new Components()
                        .addSecuritySchemes(AUTH_TOKEN_SCHEME, new SecurityScheme()
                                .name("X-Auth-Token")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Session token from /api/auth/login or /api/auth/register")));
    }
}
