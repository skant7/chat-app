package com.example.chat.service;

import com.example.chat.domain.MessageType;
import com.example.chat.dto.chat.SendMessageCommand;
import com.example.chat.model.ChatMessage;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.util.Usernames;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ChatService {

    private static final String USER_QUEUE = "/queue/messages";

    private final ChatMessageRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    public ChatService(
            ChatMessageRepository repository,
            SimpMessagingTemplate messagingTemplate,
            PresenceService presenceService) {
        this.repository = repository;
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
    }

    @Transactional
    public ChatMessage send(SendMessageCommand command) {
        if (command == null) {
            return null;
        }
        String from = Usernames.normalize(command.fromUser());
        String to = Usernames.normalize(command.toUser());
        if (from.isEmpty() || to.isEmpty() || from.equalsIgnoreCase(to)) {
            return null;
        }

        boolean hasMedia = command.mediaUrl() != null && !command.mediaUrl().isBlank();
        String body = command.text() == null ? "" : command.text().trim();
        if (!hasMedia && body.isEmpty()) {
            return null;
        }

        MessageType type = hasMedia
                ? MessageType.fromMedia(command.messageType(), command.mediaContentType())
                : MessageType.TEXT;

        ChatMessage message = repository.save(ChatMessage.create(
                from,
                to,
                body,
                type.name(),
                hasMedia ? command.mediaUrl().trim() : null,
                hasMedia ? command.mediaContentType() : null,
                hasMedia ? command.mediaFileName() : null));

        deliverToParticipants(message);
        return message;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> conversation(String a, String b) {
        return repository.findConversation(Usernames.normalize(a), Usernames.normalize(b));
    }

    @Transactional(readOnly = true)
    public List<String> contacts(String user) {
        return repository.findContacts(Usernames.normalize(user));
    }

    /** Contacts from history plus anyone currently online (except self). */
    @Transactional(readOnly = true)
    public List<String> sidebarUsers(String me) {
        String self = Usernames.normalize(me);
        Set<String> names = new LinkedHashSet<>(contacts(self));
        for (String online : presenceService.onlineUsers()) {
            if (!online.equalsIgnoreCase(self)) {
                names.add(online);
            }
        }
        return List.copyOf(names);
    }

    private void deliverToParticipants(ChatMessage message) {
        messagingTemplate.convertAndSendToUser(message.getFromUser(), USER_QUEUE, message);
        messagingTemplate.convertAndSendToUser(message.getToUser(), USER_QUEUE, message);
    }
}
