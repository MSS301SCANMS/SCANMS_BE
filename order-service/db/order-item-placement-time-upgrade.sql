-- New orders receive an absolute placement time from OrderItem.@PrePersist.
-- Existing created_at values have no timezone; do not invent historical instants.
-- A nullable column lets old order rows remain readable without changing money,
-- quantities or their original creation dates. Safe to apply repeatedly.
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS placed_at timestamp(6) with time zone;
