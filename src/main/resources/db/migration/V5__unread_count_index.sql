-- Speeds up unread aggregation: COUNT(*) WHERE to_user = ? AND status <> 'READ' GROUP BY from_user
CREATE INDEX IF NOT EXISTS idx_chat_messages_unread
    ON chat_messages (to_user, from_user)
    WHERE status <> 'READ';
