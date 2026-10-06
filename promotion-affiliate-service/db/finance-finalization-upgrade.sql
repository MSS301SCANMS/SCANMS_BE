BEGIN;
CREATE TABLE IF NOT EXISTS commission_finalizations (
 order_item_id varchar(255) PRIMARY KEY,amount_vnd bigint,finalized_at timestamp with time zone,reason varchar(500),finalized_by varchar(255)
);
CREATE TABLE IF NOT EXISTS checkout_vouchers (
 order_id varchar(255) PRIMARY KEY,customer_id varchar(255),voucher_id varchar(255),request_hash text,status varchar(255),allocations jsonb
);
CREATE INDEX IF NOT EXISTS ix_checkout_voucher_usage ON checkout_vouchers(voucher_id,customer_id,status);
COMMIT;
-- Do not invent finalization markers for historical commissions. Finalize through the source contract.
