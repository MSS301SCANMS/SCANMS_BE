-- Apply to payment_db after finance-upgrade.sql, in a maintenance window with a backup.
BEGIN;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS order_sync_status varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS resolution_reference varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS resolution_reason varchar(500);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS resolved_by varchar(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS received_amount_vnd bigint;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS resolution_amount_vnd bigint;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS surplus_refunded_amount_vnd bigint;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS surplus_reference varchar(255);
UPDATE payments SET received_amount_vnd=amount_vnd WHERE status='SUCCESS' AND received_amount_vnd IS NULL;
UPDATE payments p SET resolution_amount_vnd=t.amount_vnd FROM wallet_transactions t
 WHERE p.resolution_reference=t.transaction_id AND p.resolution_amount_vnd IS NULL;
ALTER TABLE fee_configs ADD COLUMN IF NOT EXISTS activated_at timestamp with time zone;
UPDATE fee_configs SET activated_at=COALESCE(created_at,CURRENT_TIMESTAMP) WHERE activated_at IS NULL AND status<>'DRAFT';
-- Protect legacy drafts referenced by an applied snapshot as well.
UPDATE fee_configs f SET activated_at=COALESCE(f.created_at,CURRENT_TIMESTAMP)
WHERE f.activated_at IS NULL AND EXISTS (
 SELECT 1 FROM seller_settlements s WHERE s.breakdown->'platformFee'->>'feeConfigId'=f.fee_config_id OR s.breakdown->'serviceFee'->>'feeConfigId'=f.fee_config_id
) OR (f.activated_at IS NULL AND EXISTS (SELECT 1 FROM withdrawal_requests w WHERE POSITION(f.fee_config_id IN w.fee_snapshot)>0));
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS source_kyc_reference varchar(255);
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS replaces_bank_id varchar(255);
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS verified_by varchar(255);
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS verified_at timestamp with time zone;
CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_kyc_reference ON bank_accounts(source_kyc_reference);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_replaces ON bank_accounts(replaces_bank_id);
ALTER TABLE finance_events ADD COLUMN IF NOT EXISTS blocked boolean NOT NULL DEFAULT false;
ALTER TABLE finance_events ADD COLUMN IF NOT EXISTS last_error varchar(1000);
ALTER TABLE finance_events ADD COLUMN IF NOT EXISTS last_http_status integer;
CREATE INDEX IF NOT EXISTS ix_finance_dispatch ON finance_events(next_attempt_at,created_at) WHERE delivered_at IS NULL AND blocked=false;
ALTER TABLE seller_settlements ADD COLUMN IF NOT EXISTS reconciliation_history jsonb;
UPDATE payments p SET order_sync_status='SYNCED' WHERE p.purpose='ORDER' AND p.status='SUCCESS' AND p.order_sync_status IS NULL
 AND EXISTS (SELECT 1 FROM finance_events e WHERE e.event_key='paid:'||p.payment_id AND e.delivered_at IS NOT NULL);
UPDATE payments SET order_sync_status='PENDING' WHERE purpose='ORDER' AND status='SUCCESS' AND order_sync_status IS NULL;
COMMIT;
-- Pending legacy payments without an outbox event require source/ledger reconciliation before dispatch.
SELECT payment_id,order_id FROM payments p WHERE p.order_sync_status='PENDING' AND NOT EXISTS (SELECT 1 FROM finance_events e WHERE e.event_key='paid:'||p.payment_id);
