-- Retain every existing redemption. Allow one GOODS and one SHIPPING redemption
-- on the same real order; CheckoutVoucherController enforces the benefit slots.
BEGIN;
LOCK TABLE voucher_redemptions IN ACCESS EXCLUSIVE MODE;
ALTER TABLE voucher_redemptions DROP CONSTRAINT IF EXISTS uk_voucher_redemption_order;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uk_voucher_redemption_order_voucher'
                   AND conrelid='voucher_redemptions'::regclass) THEN
        ALTER TABLE voucher_redemptions ADD CONSTRAINT uk_voucher_redemption_order_voucher UNIQUE (order_id,voucher_id);
    END IF;
END $$;
COMMIT;
