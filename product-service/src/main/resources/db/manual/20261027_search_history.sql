-- Additive PostgreSQL upgrade. Run against product_db when ddl-auto=validate.
-- Existing catalog, orders and reviews are not modified.
BEGIN;
CREATE TABLE IF NOT EXISTS search_events (
    event_id varchar(255) PRIMARY KEY,
    actor_hash varchar(64) NOT NULL,
    request_id varchar(100) NOT NULL,
    keyword varchar(200) NOT NULL,
    result_count integer NOT NULL,
    filters jsonb,
    created_at timestamptz NOT NULL,
    CONSTRAINT uk_search_actor_request UNIQUE (actor_hash, request_id)
);
CREATE INDEX IF NOT EXISTS ix_search_actor_time ON search_events (actor_hash, created_at);
CREATE INDEX IF NOT EXISTS ix_search_created ON search_events (created_at);
COMMIT;
