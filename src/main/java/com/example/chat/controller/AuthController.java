package com.example.chat.controller;

import com.example.chat.dto.auth.AuthResponse;
import com.example.chat.dto.auth.ChangePasswordRequest;
import com.example.chat.dto.auth.CredentialsRequest;
import com.example.chat.dto.auth.ForgotPasswordRequest;
import com.example.chat.dto.auth.ForgotPasswordResponse;
import com.example.chat.dto.auth.MessageResponse;
import com.example.chat.dto.auth.ResetPasswordRequest;
import com.example.chat.service.AuthService;
import com.example.chat.service.PasswordResetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin HTTP adapter for auth (DIP: depends on services, not persistence).
 * Exceptions handled by {@link com.example.chat.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody CredentialsRequest body) {
        CredentialsRequest req = body == null ? new CredentialsRequest(null, null) : body;
        AuthResponse result = authService.register(req.usernameOrEmpty(), req.passwordOrEmpty());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody CredentialsRequest body) {
        CredentialsRequest req = body == null ? new CredentialsRequest(null, null) : body;
        return authService.login(req.usernameOrEmpty(), req.passwordOrEmpty());
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@RequestBody ForgotPasswordRequest body) {
        ForgotPasswordRequest req = body == null ? new ForgotPasswordRequest(null) : body;
        return passwordResetService.requestReset(req.usernameOrEmpty());
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@RequestBody ResetPasswordRequest body) {
        ResetPasswordRequest req = body == null
                ? new ResetPasswordRequest(null, null, null, null)
                : body;
        passwordResetService.resetWithToken(req.resolvedToken(), req.resolvedNewPassword());
        return new MessageResponse("Password updated. You can log in with the new password.");
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestBody ChangePasswordRequest body) {
        ChangePasswordRequest req = body == null
                ? new ChangePasswordRequest(null, null)
                : body;
        authService.changePassword(token, req.currentPasswordOrEmpty(), req.newPasswordOrEmpty());
        return new MessageResponse("Password updated. Please log in again.");
    }

    @PostMapping("/logout")
    public ResponseEntity<MapOk> logout(
            @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        authService.logout(token);
        return ResponseEntity.ok(new MapOk(true));
    }

    public record MapOk(boolean ok) {
    }
}
