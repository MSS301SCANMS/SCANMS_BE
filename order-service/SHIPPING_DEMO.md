# Local shipping fees

The local configuration on 2026-10-22 enables a simulated fee of **30,000 VND per distinct store**, paid by the customer together with the goods. It does not call GHN or create a carrier charge.

`order-service/.env` (ignored by Git):

```properties
spring.profiles.active=local
CHECKOUT_SHIPPING_SIMULATION_ENABLED=true
CHECKOUT_SHIPPING_FEE_PER_STORE_VND=30000
```

Simulation is rejected outside local/test profiles and whenever prod/production is active. Other environments retain the existing configured fixed-fee policy; a real carrier integration is separate work.

`POST /api/v1/checkout/quote` computes goods and shipping from server-side inventory data. The browser displays the simulation notice and sends `expectedShippingFeeVnd` on checkout. A stale shipping total returns 409 before an order or inventory hold is created. Repeating an already completed checkout command preserves that order's original fee.

Each seller order captures `shippingFeeVnd`, `customerTotalVnd`, `shippingMode`, `shippingPayer`, `shippingSettlementResponsible` and `shippingSettlementStatus`. The parent order captures the aggregate shipping fee. Item net revenue and commission/merchant settlement bases exclude shipping. Payment confirmation must cover the entire order total, including shipping. Simulation snapshots mark carrier settlement as `SIMULATED`; they are not proof of a real carrier payment.

The existing item-return refund flow refunds net item payments, excluding shipping. It has not been changed to automatically refund delivery charges. Cancellation/late-payment reconciliation follows its existing order-total contract.

The Keycloak client secrets in Order/Payment `.env` were synchronized with their dedicated service accounts, with only `ORDER_INTERNAL`/`PAYMENT_INTERNAL` assigned respectively. Restart **order-service** and **payment-service** in IntelliJ, then hard-refresh the frontend to load these changes. No additional application instance is left running on the main service ports.

Validation includes multi-store totals, stale quotes, idempotent retries after a policy change, simulated-mode restrictions, payment total checks, order-history breakdown and browser wallet checkout. A disposable in-memory order service also verifies the real Keycloak/product inventory quote: goods 269,000 + shipping 30,000 = 299,000 VND. Real user wallets are not charged by these checks. Local receipts are under `be/.local/shipping-demo-20261022/`.
