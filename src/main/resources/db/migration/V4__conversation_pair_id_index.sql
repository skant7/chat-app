-- Speeds up keyset conversation pages: pair filter + id < cursor ORDER BY id DESC.
CREATE INDEX IF NOT EXISTS idx_chat_messages_from_to_id
    ON chat_messages (from_user, to_user, id DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_to_from_id
    ON chat_messages (to_user, from_user, id DESC);
