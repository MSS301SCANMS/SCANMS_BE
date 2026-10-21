-- Additive migration for merchant return statements; preserves customer evidence.
ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS shop_response jsonb;
