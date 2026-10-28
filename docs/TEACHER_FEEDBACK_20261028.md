# Teacher feedback: implementation and operation

## Business rules

| Feedback | Implemented behavior |
| --- | --- |
| KOL disconnects / relay crashes | Signed, ordered publisher heartbeats. Missing heartbeats become RECONNECTING after 30 seconds, then allow 120 seconds to reconnect. A normal disconnect starts the grace window immediately. Product closes the live after that deadline or its scheduled end. New live deals pause; existing reservations/orders retain captured prices. |
| Two buyers want the last unit | Database row locks protect authoritative variant stock in the reservation transaction. Exactly one reservation takes the last unit. Cart/quote does not reserve stock; reservation retries are idempotent. |
| Cancel an order | Buyer cancels the whole order before carrier handover. All seller orders PENDING: cancel immediately. Otherwise all shops must approve. Rejection resumes fulfillment. Pending cancellation blocks confirmation/dispatch; cancellation and handover lock the same parent order. |
| Search history | Records submitted searches, result counts and whitelisted filters. Idempotent request IDs; private history/clear scoped to JWT subject, guest events scoped to tab session. Actor IDs are hashed; tokens are never stored. Default retention: 30 days. |
| Review text | Create/edit reject dictionary profanity, including separated letters. Vietnamese accents prevent false positives such as “lớn”. Negative product reviews remain allowed. This is dictionary moderation, not a comprehensive AI filter. |
| Homepage sold count | Batched Order aggregates: delivered/completed paid quantities minus refunded returns. Stock still validates purchases. Unavailable Order data shows unknown count rather than invented sales. |
| Insufficient wallet | FE shows balance, total and shortage with top-up/reload/change-payment actions. Order checks the buyer wallet before a new wallet checkout/reservation. Payment locks and rechecks balance at debit, protecting concurrent spending. READY retries remain idempotent. |
| Listed goods subtotal | Quantity × listed variant unit price. Direct/live savings, goods vouchers, shipping and shipping vouchers are separate; live savings are subtracted once. A live offer above a newly reduced catalog price requires refresh. |

## Cancellation money and contracts

Verified captured payments are refunded to the customer's **SCANMS wallet**, including orders paid through PayOS. This does not refund a bank account. Ledger key `cancelled-order-payment:<paymentId>` and an acknowledgement outbox prevent duplicate credit. Identity, currency and amount must match authoritative cancelled-order data.

Order retries inventory/voucher restoration, affiliate cancellation and payment reconciliation. NOT_REQUIRED means no captured payment was found yet; subsequent scans can recover late captures. Provider payment-link cancellation is not implemented; order cancellation is not confirmation that a PayOS link has closed.

New business services use interface/implementation classes, DTOs and OpenFeign. Search responses use MapStruct. Cancellation state/history uses existing Order saga JSON; each service retains its own persistence.

- `POST /api/v1/orders/{id}/cancellation`: buyer JWT, `{reason}`.
- `POST /api/v1/commerce/seller-orders/{id}/cancellation/decision`: owning shop JWT, `{approved, reason}`; rejection requires a reason.
- `POST /api/v1/finance/order-cancellations/{id}/refund`: internal Order identity to Payment.
- `GET /api/v1/finance/orders/{id}/cancellation-refund-basis`, `POST /api/v1/finance/orders/{id}/cancellation-refunded`: direct internal Order contracts. Use the Order URL, because Gateway's `/finance` route goes to Payment.
- `POST /api/v1/public/search-events`; private `GET` / `DELETE /api/v1/search-history`.
- `GET /api/v1/store-sales/products?productIds=...`: public batch aggregate, no buyer/order details.
- `POST /api/v1/livestreams/{id}/broadcast`: relay JWT and HMAC; never expose the secret in VITE variables.

## Livestream technology

Browser **WebRTC peer-to-peer** transmits camera/audio. Socket.IO supplies signaling, chat, presence, pins and reactions. STUN discovers addresses; TURN relays media when peers cannot connect directly. Spring Product owns lifecycle/commercial rules; the Node relay owns active connections. Publisher heartbeat alone does not prove a viewer receives frames; a separate browser test checks decoded video frames and received audio bytes.

The publisher sends a media connection per subscriber. Default cap: 20 media subscribers, including the shop moderator. This is a configured limit, not a measured production capacity. More viewers need an SFU/media server and load testing. Multiple relay replicas also need shared room/counter/idempotency coordination. This change does not add an SFU.

Background: [WebRTC protocols](https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API/Protocols), [Socket.IO](https://socket.io/docs/v4/), [mediasoup architecture](https://mediasoup.org/documentation/overview/).

## Upgrade

With `JPA_DDL_AUTO=validate`, apply these additive Product scripts before restart:

1. `product-service/src/main/resources/db/manual/20261027_search_history.sql`.
2. `product-service/src/main/resources/db/manual/20261028_store_chat.sql` when develop chat tables are missing.

Both were applied to local `localhost:5437/product_db` on 28 October; full Hibernate schema validation passed. Counts remained 24 products, 96 variants, 69 images and 1 store. Other machines/databases must apply the scripts themselves. Older schemas still need the previous statistics/short-video migrations.

Restart Gateway, Product, Order, Payment and relay; refresh FE. Retain existing Order/Payment/Affiliate internal identities/roles for reconciliation. Product and relay must share `LIVE_METRICS_SECRET`, at least 32 characters; never commit secrets. Default heartbeat/grace can be changed with `scanms.live.heartbeat-timeout-seconds` / `scanms.live.reconnect-grace-seconds`.

## Validation limits

Final complete reactor run on 28 October: **360 executed tests passed, 13 optional cases skipped** (373 cases in the run). A separate real PostgreSQL run passed **15/15** inventory and live-commerce tests, including last-unit contention and offer limits. Only newly created `scanms_qa_product` / `scanms_qa_live_commerce` databases were used; both were removed afterward. The application's Product database was not used for destructive tests.

| Module | Cases | Skipped | Failed/errors |
| --- | ---: | ---: | ---: |
| User | 39 | 0 | 0 |
| Product | 92 | 8 | 0 |
| Order | 84 | 1 | 0 |
| Payment | 66 | 2 | 0 |
| Promotion/Affiliate | 92 | 2 | 0 |

Integration tests cover cancellation ownership, multi-shop decisions, handover races, idempotent refunds, late capture, last-unit contention, moderation, search isolation and pricing/wallet checks. Browser fixtures use actual FE components/API clients with controlled responses. The camera test uses real WebRTC between browser contexts with synthetic devices. These do not prove real PayOS webhooks, production load or mobile connectivity across unrelated networks.
