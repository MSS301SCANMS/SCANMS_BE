# Local purchase, delivery, returns and wallet refunds

Run `order-service`, `payment-service`, `user-service`, `product-service`, the gateway and Keycloak. Order and Payment require their existing confidential service credentials (`ORDER_INTERNAL` and `PAYMENT_INTERNAL`).

Shipping simulation requires **both** the `local` Spring profile and `SHIPPING_SIMULATION_ENABLED=true` in `order-service/.env`. It is disabled by default and cannot run when a `prod` or `production` profile is active. In IntelliJ set **Active profiles** to `local` for Order. The additive migration for databases managed with `ddl-auto=validate` is [db/commerce-lifecycle-upgrade.sql](db/commerce-lifecycle-upgrade.sql).

## Purchase and delivery

1. Customer checks out and pays using the existing wallet payment API. The durable payment acknowledgement marks the order paid. Unpaid orders cannot be dispatched.
2. In Shop → Orders, click **Giao hàng** → **Giao hàng mô phỏng**. This atomically confirms the shop order and saves one `LOCAL_SIMULATION` shipment. Retries reuse that shipment. No GHN call is made.
3. Open tracking, or the customer's order details. Click **Bắt đầu mô phỏng giao hàng**, then **Xác nhận giao thử thành công**. Delivery starts the 14-day return window; retrying cannot extend it.
4. Customer can confirm receipt once every parcel is delivered. This does not remove the remaining return window.

Live carrier booking is not implemented. The UI disables that option. Existing real waybills can be entered manually; these never expose simulation controls.

## Physical return and refund

1. Customer selects an order item and quantity, supplies images and an unboxing video, and sends a request within 14 days of delivery. Active/previously refunded quantities cannot be requested twice. Creation has an idempotency key and locks the purchased item against concurrent excess requests.
2. Shop supplies its statement in **Yêu cầu đổi trả**. Shop statements do not approve refunds. Admin/Manager decides in `/admin/return-disputes`; only an undecided request can be approved/rejected. Customer may cancel an undecided request.
3. After approval the customer opens `/customer/returns/{id}` and sends the parcel back, either through local simulation or an existing manual carrier waybill. For a simulated return, advance pickup and delivery to the shop explicitly.
4. Shop opens the request from `/merchant/return-requests` and confirms receipt. Until this step, approval alone cannot trigger a refund.
5. Order's persisted dispatcher requests a refund; Payment derives the customer and amount from Order's immutable paid item snapshot, posts exactly one `REFUND` credit, then durably acknowledges the real transaction ID. The UI shows `REFUNDED` only after this acknowledgement.

Refunds go to the **buyer's CUSTOMER wallet in SCANMS**, including purchases originally paid through PayOS. They do not transfer money back to a bank/card. Refunds cover the returned item's net paid amount after discounts, excluding shipping. Partial refunds allocate any final VND remainder without exceeding the purchased quantity or net paid value.

`RECEIVED`/`REFUND_PENDING` remains pending while a dependency is unavailable and is retried by the existing dispatcher/outbox. No synthetic refund reference is recorded. Default dispatch intervals are 60 seconds (Order) and 10 seconds (Payment). Active returns block seller settlement. Returned/damaged goods are not automatically put back on sale.

## Verification (2026-10-22)

Dedicated local QA identities were used; no personal account balance was adjusted. Real Keycloak, gateway, PostgreSQL, Order and Payment APIs verified:

- Wallet purchase: 2,000,000 → 1,013,000 VND for 3 × 329,000 VND.
- One-item refund: 1,013,000 → 1,342,000 VND; one ledger entry and a real transaction acknowledgement.
- Remaining two-item refund: 658,000 VND; original order total refunded exactly 987,000 VND.
- Repeated payment, dispatch, receipt and refund commands do not duplicate monetary/transport effects.
- Unpaid dispatch, foreign customer access, customer/shop self-approval, premature refund, rejected/cancelled refund and over-quantity requests are rejected.
- Two concurrent two-item requests against two remaining items produce one accepted request and one conflict on PostgreSQL.
- A second 329,000 VND order was returned end to end through the customer/shop screens. Final QA wallet: 2,000,000 VND, held balance 0. Two payment entries total 1,316,000 VND; three refund entries total exactly 1,316,000 VND.
- Final read-only audit found no historical `REFUND_OFFLINE_SYNC`/synthetic `REFUND_TX_*` references and no `REFUNDED` request without a reference in the local Order database.

Validation: 64 backend tests passed (one optional standalone PostgreSQL migration test skipped in the H2 suite), eight focused frontend tests passed, and FE/Order/Payment/Gateway builds passed. PostgreSQL behavior was also verified through the running application APIs above.

QA receipts and account credentials are stored privately under `be/.local/commerce-qa-20261022/`; that directory must remain ignored. Automated tests cover return rounding, concurrent reservations, expired windows, receipt after delivery, no fake credit on approval, and unresolved returns blocking settlement. A separate browser test exercises actual commerce APIs while stubbing only the evidence upload boundary locally.

After the user explicitly approved external QA uploads, one 677-byte PNG and one 3,496-byte WEBM (64×64, approximately one second) were uploaded through the real gateway/User Service endpoints to `scanms/qa/commerce-20261022`. Both backend uploads and Cloudinary CDN retrievals returned HTTP 200 with the correct media type and byte count. Receipts: `be/.local/commerce-qa-20261022/cloudinary-upload-result.json`. No additional order, return or wallet transaction was created by this upload test.
