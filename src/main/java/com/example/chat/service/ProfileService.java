package com.example.chat.service;

import com.example.chat.dto.profile.ProfileResponse;
import com.example.chat.dto.profile.UpdateProfileRequest;
import com.example.chat.exception.AuthException;
import com.example.chat.model.UserAccount;
import com.example.chat.repository.AuthSessionRepository;
import com.example.chat.repository.ChatGroupRepository;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.GroupMemberRepository;
import com.example.chat.repository.PasswordResetTokenRepository;
import com.example.chat.repository.UserAccountRepository;
import com.example.chat.util.Usernames;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Own-profile read/update. Username changes cascade across denormalized identity columns
 * (sessions, messages, groups) so STOMP principals and history stay consistent.
 */
@Service
public class ProfileService {

    public static final int MAX_ABOUT_LENGTH = 200;

    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordResetTokenRepository resetTokens;
    private final ChatMessageRepository messages;
    private final ChatGroupRepository groups;
    private final GroupMemberRepository groupMembers;
    private final PresenceService presenceService;

    public ProfileService(
            UserAccountRepository users,
            AuthSessionRepository sessions,
            PasswordResetTokenRepository resetTokens,
            ChatMessageRepository messages,
            ChatGroupRepository groups,
            GroupMemberRepository groupMembers,
            PresenceService presenceService) {
        this.users = users;
        this.sessions = sessions;
        this.resetTokens = resetTokens;
        this.messages = messages;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.presenceService = presenceService;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String sessionUsername) {
        return ProfileResponse.from(requireAccount(sessionUsername));
    }

    @Transactional
    public ProfileResponse updateProfile(String sessionUsername, UpdateProfileRequest request) {
        UserAccount account = requireAccount(sessionUsername);
        String oldUsername = account.getUsername();

        UpdateProfileRequest body = request == null
                ? new UpdateProfileRequest(null, null)
                : request;

        // Validate fully before mutating the entity (avoids mid-method auto-flush of half-applied state).
        String about = account.getAbout() == null ? "" : account.getAbout();
        if (body.about() != null) {
            about = body.aboutOrEmpty();
            if (about.length() > MAX_ABOUT_LENGTH) {
                throw new AuthException("About is too long (max " + MAX_ABOUT_LENGTH + " characters)");
            }
        }

        String requestedUsername = Usernames.normalize(body.usernameOrEmpty());
        // Blank username means keep current; non-blank must pass validation.
        String newUsername = oldUsername;
        if (!requestedUsername.isEmpty()) {
            Usernames.requireValid(requestedUsername);
            newUsername = requestedUsername;
        }

        boolean rename = !oldUsername.equals(newUsername);
        if (rename && !oldUsername.equalsIgnoreCase(newUsername)
                && users.existsByUsernameIgnoreCase(newUsername)) {
            throw AuthException.conflict("User already exists");
        }

        account.setAbout(about);
        if (rename) {
            account.setUsername(newUsername);
        }

        try {
            // Flush before cascade bulk updates: those use clearAutomatically and would
            // otherwise discard an unflushed username change from the persistence context.
            users.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw AuthException.conflict("User already exists");
        }

        if (rename) {
            cascadeUsernameChange(oldUsername, newUsername);
            presenceService.rename(oldUsername, newUsername);
        }

        return ProfileResponse.from(account);
    }

    private UserAccount requireAccount(String sessionUsername) {
        String name = Usernames.normalize(sessionUsername);
        if (name.isEmpty()) {
            throw new AuthException("Not authenticated");
        }
        return users.findByUsernameIgnoreCase(name)
                .orElseThrow(() -> new AuthException("Not authenticated"));
    }

    private void cascadeUsernameChange(String oldUsername, String newUsername) {
        sessions.updateUsername(oldUsername, newUsername);
        resetTokens.updateUsername(oldUsername, newUsername);
        messages.renameFromUser(oldUsername, newUsername);
        messages.renameToUser(oldUsername, newUsername);
        groups.renameCreatedBy(oldUsername, newUsername);
        groupMembers.renameUsername(oldUsername, newUsername);
    }
}
