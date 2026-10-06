-- Apply only to order_db. Duplicate keys or invalid historical refunds need reconciliation, not deletion.
BEGIN;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS checkout_request text;
CREATE UNIQUE INDEX IF NOT EXISTS uk_order_customer_key ON orders(customer_id,idempotency_key);
COMMIT;
SELECT r.order_item_id,SUM(r.quantity) AS reserved_quantity,SUM(r.refund_amount_vnd) AS reserved_vnd
FROM return_requests r JOIN order_items i ON i.order_item_id=r.order_item_id
WHERE r.status IN ('REFUND_PENDING','REFUNDED') OR r.refund_reference IS NOT NULL
GROUP BY r.order_item_id,i.quantity,i.net_paid_amount_vnd
HAVING SUM(r.quantity)>i.quantity OR SUM(r.refund_amount_vnd)>i.net_paid_amount_vnd;
SELECT return_request_id FROM return_requests WHERE status IN ('REFUND_PENDING','REFUNDED') AND (refund_amount_vnd IS NULL OR refund_amount_vnd<=0);
