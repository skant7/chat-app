package com.example.chat.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCatchupListenerTest {

    @Test
    void recognizesPrivateMessageQueueDestinations() {
        assertTrue(MessageCatchupListener.isPrivateMessageQueue("/user/queue/messages"));
        assertTrue(MessageCatchupListener.isPrivateMessageQueue("/user/bob/queue/messages"));
        assertFalse(MessageCatchupListener.isPrivateMessageQueue("/topic/presence"));
        assertFalse(MessageCatchupListener.isPrivateMessageQueue(null));
        assertFalse(MessageCatchupListener.isPrivateMessageQueue(""));
    }
}
