-- Additive, repeatable migration; old shipments remain ineligible for simulation.
ALTER TABLE shipments ADD COLUMN IF NOT EXISTS environment varchar(255);
ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS request_key varchar(128);
ALTER TABLE return_requests ADD COLUMN IF NOT EXISTS logistics jsonb;
