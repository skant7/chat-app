package com.example.chat.controller;

import com.example.chat.config.OpenApiConfig;
import com.example.chat.dto.group.CreateGroupRequest;
import com.example.chat.dto.group.GroupMemberResponse;
import com.example.chat.dto.group.GroupResponse;
import com.example.chat.exception.AuthException;
import com.example.chat.model.ChatMessage;
import com.example.chat.service.GroupService;
import com.example.chat.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@Tag(name = "Groups", description = "Create groups, list membership, and group message history")
@SecurityRequirement(name = OpenApiConfig.AUTH_TOKEN_SCHEME)
public class GroupController {

    private final GroupService groupService;
    private final SessionService sessionService;

    public GroupController(GroupService groupService, SessionService sessionService) {
        this.groupService = groupService;
        this.sessionService = sessionService;
    }

    @Operation(summary = "Create a group (creator is ADMIN; optional member usernames)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestBody CreateGroupRequest body) {
        return groupService.create(requireUser(token), body);
    }

    @Operation(summary = "List groups for the authenticated user")
    @GetMapping
    public List<GroupResponse> list(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        return groupService.listForUser(requireUser(token));
    }

    @Operation(summary = "List members of a group (must be a member)")
    @GetMapping("/{id}/members")
    public List<GroupMemberResponse> members(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable("id") Long id) {
        return groupService.listMembers(requireUser(token), id);
    }

    @Operation(summary = "Group message history (must be a member)")
    @GetMapping("/{id}/messages")
    public List<ChatMessage> messages(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable("id") Long id) {
        return groupService.history(requireUser(token), id);
    }

    private String requireUser(String token) {
        return sessionService.resolveUsername(token)
                .orElseThrow(() -> new AuthException("Not authenticated"));
    }
}
