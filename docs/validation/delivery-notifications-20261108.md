# Customer delivery notifications — 2026-11-08

## Behavior

Authenticated buyers can list delivery notifications through Gateway at `GET /api/v1/notifications`, with category/pagination, and retrieve badge counts at `/unread-count`. `PATCH /{id}/read` and `/read-all` persist read receipts scoped to the verified business customer ID. Caller-supplied user IDs never select a recipient.

The durable source is `SellerOrder.deliveredAt`, written by the existing shipment delivery and simulated delivery flows. Each delivered parcel has one stable `delivery-{sellerOrderId}` ID. Historical deliveries appear automatically; completing or returning an order does not erase its delivery history. There are no added payment, order-status or shipping mutations. Opening a notification does not confirm receipt of goods.

The response includes an explicit buyer-order deep link, including both order and parcel IDs. This also works when a shop owner or KOL bought as a customer. This endpoint currently supplies delivery notices; it does not implement all order lifecycle, promotion or system notification events.

## Installation

With `ddl-auto=validate`, run `order-service/src/main/resources/db/manual/20261108_delivery_notification_reads.sql` on the Order database before restarting Order. The script is idempotent and only creates the read-receipt table and index. Restart `order-service` and `scanms-gateway`; reload the web app. This migration was applied to the local Order database during validation.

## Validation

- `OrderNotificationIntegrationTest` and `CommerceLifecycleIntegrationTest`: **14 passed**, including simulated delivery, duplicate delivery callbacks, historical orders, read/read-all, pagination, authentication, ownership and concurrent read retries.
- `GatewayOpsIntegrationTest`: **7 passed**, including authenticated notification list/count/read routes and rejection without login.
- Read-only probe against local PostgreSQL: Hibernate schema validation and all delivery repository queries passed. **8 orders and 4 delivered parcels remained present**. No read receipts were created by the probe.
- Related FE build/lint and full-App browser scenario passed; see the FE validation note.

Gateway tests use an upstream stub, Order integration tests use H2, and the PostgreSQL probe reads actual local data. No external shipping delivery, bank transfer, email or mobile push was exercised.
