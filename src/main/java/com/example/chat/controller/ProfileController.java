package com.example.chat.controller;

import com.example.chat.config.OpenApiConfig;
import com.example.chat.dto.profile.ProfileResponse;
import com.example.chat.dto.profile.UpdateProfileRequest;
import com.example.chat.exception.AuthException;
import com.example.chat.service.ProfileService;
import com.example.chat.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile", description = "View and update the authenticated user's username and about")
@SecurityRequirement(name = OpenApiConfig.AUTH_TOKEN_SCHEME)
public class ProfileController {

    private final ProfileService profileService;
    private final SessionService sessionService;

    public ProfileController(ProfileService profileService, SessionService sessionService) {
        this.profileService = profileService;
        this.sessionService = sessionService;
    }

    @Operation(summary = "Get the current user's profile")
    @GetMapping
    public ProfileResponse get(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        return profileService.getProfile(requireUser(token));
    }

    @Operation(summary = "Update username and/or about text")
    @PutMapping
    public ProfileResponse update(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestBody UpdateProfileRequest body) {
        return profileService.updateProfile(requireUser(token), body);
    }

    private String requireUser(String token) {
        return sessionService.resolveUsername(token)
                .orElseThrow(() -> new AuthException("Not authenticated"));
    }
}
