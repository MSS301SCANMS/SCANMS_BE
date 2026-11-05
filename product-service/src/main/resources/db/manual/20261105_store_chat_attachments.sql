-- Additive migration. File bytes stay private in Product DB, never in public image URLs.
BEGIN;
CREATE TABLE IF NOT EXISTS store_chat_attachments (
    id varchar(255) PRIMARY KEY,
    conversation_id varchar(255) NOT NULL,
    uploader_id varchar(255) NOT NULL,
    file_name varchar(255) NOT NULL,
    content_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL,
    created_at timestamptz NOT NULL,
    content bytea NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chat_attachment_conversation ON store_chat_attachments(conversation_id);
COMMIT;
