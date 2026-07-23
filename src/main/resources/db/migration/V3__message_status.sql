-- Message delivery lifecycle: SENT -> DELIVERED -> READ

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'SENT';

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS delivered_at BIGINT;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS read_at BIGINT;

-- Legacy rows: treat as already delivered historically (status stays SENT until clients report;
-- backfill SENT only so column is populated).
UPDATE chat_messages
SET status = 'SENT'
WHERE status IS NULL OR status = '';
