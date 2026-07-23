-- clientMessageId: ensure column, backfill legacy NULLs, enforce uniqueness + NOT NULL.
-- Multiple NULLs are distinct in a UNIQUE index, so legacy rows need real values.

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(64);

-- Stable unique backfill per row (global id is unique → safe under (from_user, client_message_id)).
UPDATE chat_messages
SET client_message_id = 'legacy-' || id::text
WHERE client_message_id IS NULL;

ALTER TABLE chat_messages
    ALTER COLUMN client_message_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_chat_messages_from_client_msg_id'
    ) THEN
        ALTER TABLE chat_messages
            ADD CONSTRAINT uk_chat_messages_from_client_msg_id
            UNIQUE (from_user, client_message_id);
    END IF;
END
$$;
