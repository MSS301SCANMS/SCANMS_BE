-- Run manually against payment_db, with a backup and deployment paused.
-- This migrates the existing skeleton's tables; it does not invent bank snapshots or alter balances.
BEGIN;
ALTER TABLE fee_configs ALTER COLUMN rate_percent TYPE numeric(10,6);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payer_id varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS wallet_id varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS purpose varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_order_code bigint;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS active_order_ref varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS checkout_url varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS qr_code text;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS expires_at timestamp;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0;
UPDATE payments SET version=0 WHERE version IS NULL;
ALTER TABLE payments ALTER COLUMN version SET NOT NULL;
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS idempotency_key varchar(255);
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS fee_vnd bigint;
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS net_amount_vnd bigint;
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS fee_snapshot text;
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS destination_snapshot text;
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS scheduled_for timestamp with time zone;
ALTER TABLE withdrawal_requests ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0;
UPDATE withdrawal_requests SET version=0 WHERE version IS NULL;
ALTER TABLE withdrawal_requests ALTER COLUMN version SET NOT NULL;
ALTER TABLE withdrawal_requests ALTER COLUMN fee_snapshot TYPE text;
ALTER TABLE withdrawal_requests ALTER COLUMN destination_snapshot TYPE text;
ALTER TABLE withdrawal_requests ALTER COLUMN failure_reason TYPE varchar(500);
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS held_before_vnd bigint;
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS held_after_vnd bigint;
ALTER TABLE seller_settlements ADD COLUMN IF NOT EXISTS seller_order_id varchar(255);
ALTER TABLE seller_settlements ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0;
UPDATE seller_settlements SET version=0 WHERE version IS NULL;
ALTER TABLE seller_settlements ALTER COLUMN version SET NOT NULL;
-- If any index fails because of duplicates, ROLLBACK and reconcile those rows; never delete ledger rows.
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_key ON payments(idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_provider_order ON payments(provider_order_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_active_order ON payments(active_order_ref);
CREATE UNIQUE INDEX IF NOT EXISTS uk_withdrawal_key ON withdrawal_requests(idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_settlement_seller_order ON seller_settlements(seller_order_id);
CREATE TABLE IF NOT EXISTS finance_events (
    event_id varchar(255) PRIMARY KEY,
    event_key varchar(255) UNIQUE,
    destination varchar(255),
    payload jsonb,
    created_at timestamp with time zone,
    delivered_at timestamp with time zone,
    next_attempt_at timestamp with time zone,
    attempts integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0
);
COMMIT;

-- Review pre-existing data separately. Legacy bank ciphertext must use the configured AES-GCM key.
-- Legacy payments without payer_id/purpose and withdrawals without snapshots need business reconciliation.
SELECT payment_id, order_id, status FROM payments WHERE payer_id IS NULL OR purpose IS NULL;
SELECT withdrawal_id, status FROM withdrawal_requests WHERE destination_snapshot IS NULL OR fee_vnd IS NULL;
SELECT wallet_id, available_balance_vnd, held_balance_vnd FROM wallets
WHERE available_balance_vnd < 0 OR held_balance_vnd < 0;
