-- Additive migration. Run on Promotion DB when ddl-auto=validate.
BEGIN;
ALTER TABLE product_commission_deals ADD COLUMN IF NOT EXISTS proposed_by_user_id varchar(255);
ALTER TABLE product_commission_deals ADD COLUMN IF NOT EXISTS negotiation_history jsonb;
CREATE TABLE IF NOT EXISTS sample_requests (
    id varchar(255) PRIMARY KEY,
    collaborator_id varchar(255) NOT NULL,
    customer_id varchar(255) NOT NULL,
    store_id varchar(255) NOT NULL,
    product_id varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    note varchar(2000) NOT NULL,
    response varchar(1000),
    recipient_name varchar(255) NOT NULL,
    recipient_phone varchar(255) NOT NULL,
    shipping_address varchar(2000) NOT NULL,
    carrier varchar(255), tracking_code varchar(255),
    created_at timestamptz, shipped_at timestamptz, received_at timestamptz,
    product jsonb, version bigint
);
CREATE INDEX IF NOT EXISTS idx_sample_participant ON sample_requests(collaborator_id, product_id, status);
COMMIT;
