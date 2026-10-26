# Livestream integration with develop — 2026-10-27

Branch: `feat/livestream-commerce-sync-20261027`.
Develop integrated: `99607d1b0892b4ff3c7119912b99c5dc5c96e428`.

## Changes and compatibility

- Preserve scoped live offers, voucher drops, signed purchase access, durable pinning and shared audience/reaction counters.
- Keep controllers, request/response DTOs, service interfaces/implementations, MapStruct mappers and OpenFeign clients. Format changed Java code with IntelliJ's formatter.
- Merge develop's Kafka and service configuration changes. Remove the forced PostgreSQL dialect so Hibernate selects PostgreSQL in development and H2 in integration tests.
- Keep existing order snapshots and checkout amount checks. Updating a live offer does not change the catalog price.

## Verification

- Full Maven reactor `mvn test`: successful for all six services; 301 tests, zero failures/errors, five skipped (Gateway has no test sources).
- After controller import/format cleanup, Product's `LiveCommerceIntegrationTest`, `LiveMetricsIntegrationTest` and `LivestreamLifecycleIntegrationTest`: 14 tests passed.
- Realtime relay `npm test`: four tests passed.
- Browser fixtures on the companion FE branch verify KOL video/audio to customer and shop, audience deduplication, shared hearts, reconnection, pin persistence, live variant pricing and voucher publication. Fixtures do not create real orders or charge wallets.

## Runtime notes

- Restart Product/Order and the realtime relay to load updated code; an already running IntelliJ service continues using its previous classes.
- Keep each service's private `.env` values outside Git. This merge does not change or reset the application database.
- Media currently uses WebRTC peer connections. Browser verification is functional testing, not a large-audience load test; large-scale broadcasting still requires a media SFU/CDN deployment.
