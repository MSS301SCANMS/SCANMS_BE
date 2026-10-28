-- Store chat tables introduced by the existing develop branch.
-- Additive and repeatable; no catalog data is updated or deleted.
BEGIN;
CREATE TABLE IF NOT EXISTS store_chat_conversations (
    id varchar(255) PRIMARY KEY,
    store_id varchar(255) NOT NULL,
    customer_id varchar(255) NOT NULL,
    customer_name varchar(255),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uk_store_chat_customer UNIQUE (store_id, customer_id)
);
CREATE TABLE IF NOT EXISTS store_chat_messages (
    id varchar(255) PRIMARY KEY,
    conversation_id varchar(255) NOT NULL,
    sender_id varchar(255) NOT NULL,
    message_text varchar(10000) NOT NULL,
    created_at timestamptz NOT NULL,
    is_read boolean NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_store_chat_messages ON store_chat_messages (conversation_id, created_at);
COMMIT;
