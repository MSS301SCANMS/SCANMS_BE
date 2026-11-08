-- Read receipts only. Delivery history remains sourced from seller_orders.delivered_at.
-- Additive/repeatable; no order, payment, wallet or shipment data is changed.
BEGIN;
CREATE TABLE IF NOT EXISTS order_notification_reads (
    id varchar(255) PRIMARY KEY,
    user_id varchar(255) NOT NULL,
    seller_order_id varchar(255) NOT NULL,
    read_at timestamptz NOT NULL,
    CONSTRAINT uk_delivery_notice_reader UNIQUE(user_id, seller_order_id)
);
CREATE INDEX IF NOT EXISTS idx_delivery_notice_reader ON order_notification_reads(user_id);
COMMIT;
