-- Run with the updated payment-service. Preserve historical credits and add referenced reversals.
-- KOL available balance may be negative only after recovering a commission already withdrawn;
-- subsequent earnings offset that debt, and withdrawal holds still require sufficient balance.
ALTER TABLE wallet_transactions DROP CONSTRAINT IF EXISTS wallet_transactions_type_check;
ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transactions_type_check CHECK
  (type IN ('TOP_UP','ORDER_PAYMENT','REFUND','SELLER_SETTLEMENT','KOL_COMMISSION',
            'COMMISSION_REVERSAL','PLATFORM_FEE','WITHDRAWAL','ADJUSTMENT','HOLD','RELEASE'));
CREATE INDEX IF NOT EXISTS idx_wallet_transaction_commission_ref
  ON wallet_transactions(wallet_id, reference_type, reference_id);
