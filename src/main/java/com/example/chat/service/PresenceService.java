package com.example.chat.service;

import com.example.chat.util.Usernames;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks who is currently connected (by display name). */
@Service
public class PresenceService {

    private final Set<String> online = ConcurrentHashMap.newKeySet();
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void join(String username) {
        String name = Usernames.normalize(username);
        if (name.isEmpty()) {
            return;
        }
        online.add(name);
        broadcast();
    }

    public void leave(String username) {
        String name = Usernames.normalize(username);
        if (name.isEmpty()) {
            return;
        }
        online.remove(name);
        broadcast();
    }

    /** If the user is online under {@code oldUsername}, re-key presence to {@code newUsername}. */
    public void rename(String oldUsername, String newUsername) {
        String oldName = Usernames.normalize(oldUsername);
        String newName = Usernames.normalize(newUsername);
        if (oldName.isEmpty() || newName.isEmpty() || oldName.equals(newName)) {
            return;
        }
        if (online.remove(oldName)) {
            online.add(newName);
            broadcast();
        }
    }

    public Set<String> onlineUsers() {
        return Set.copyOf(online);
    }

    private void broadcast() {
        messagingTemplate.convertAndSend("/topic/presence", onlineUsers());
    }
}
