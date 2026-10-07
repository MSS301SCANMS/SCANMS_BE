-- Preserve the receiving account supplied by payOS for each payment link.
-- Existing payment rows remain unchanged; missing details are not guessed.
BEGIN;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS bin varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS account_number varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS account_name varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS description varchar(255);
COMMIT;
