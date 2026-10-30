-- Ensure users.fashion_preferences exists (Hibernate ddl-auto=update also adds it).
-- Run against user_db (port 5433):
--   psql -h 127.0.0.1 -p 5433 -U postgres -d user_db -f backfill-user-fashion-prefs.sql

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS fashion_preferences jsonb DEFAULT '{}'::jsonb;

UPDATE users
SET fashion_preferences = '{}'::jsonb
WHERE fashion_preferences IS NULL;

-- Seed empty preference shell for existing customers (safe no-op if already set)
UPDATE users
SET fashion_preferences = jsonb_build_object(
  'preferredColors', '[]'::jsonb,
  'preferredStyles', '[]'::jsonb,
  'preferredGenderTarget', CASE gender
    WHEN 'MALE' THEN 'MALE'
    WHEN 'FEMALE' THEN 'FEMALE'
    ELSE 'UNISEX'
  END
)
WHERE fashion_preferences = '{}'::jsonb
   OR fashion_preferences IS NULL
   OR NOT (fashion_preferences ? 'preferredGenderTarget');

SELECT count(*) AS users_with_prefs
FROM users
WHERE fashion_preferences IS NOT NULL;
