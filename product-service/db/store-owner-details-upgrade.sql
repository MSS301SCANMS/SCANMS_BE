-- Preserve existing stores and keep private owner/warehouse details out of public DTOs.
ALTER TABLE stores ADD COLUMN IF NOT EXISTS owner_details jsonb;
