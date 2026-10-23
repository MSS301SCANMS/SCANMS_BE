-- Preserve existing profiles while expanding storage for the JSON address book.
-- Safe to run more than once against user_db.
BEGIN;
SET LOCAL lock_timeout = '5s';
ALTER TABLE users ALTER COLUMN address TYPE text;
COMMIT;
