# Develop merge and registration review — 2026-10-27

Branch: `feat/livestream-commerce-sync-20261027`.
Develop merged: `e5b58bc` (PR #47).

## Merge resolution

Three conflicts were resolved in `CheckoutService`, `CheckoutServiceImpl` and `CheckoutIntegrationTest`.

- Keep develop's shipping destination contract, address consistency check, GHN sandbox quote service and per-store shipping snapshots/discount inputs.
- Preserve OpenFeign inventory quote/reservation calls with buyer identity and live purchase proof. Live prices and product origin remain authoritative from Product; the catalog price is unchanged by a live offer.
- Preserve expected payable validation, captured totals and safe idempotent retries. Keep quoted line data required by the companion FE.
- Keep develop's commission timestamps and verified-click attribution. Bare referral IDs do not establish an affiliate attribution; expired clicks do not block an ordinary purchase. Update the combined integration fixtures to exercise verified clicks per product, and remove duplicate fixture contexts/test methods introduced by the merge.
- Remove a duplicate image query/local variable in the automatically merged inventory controller. Preserve the deterministic primary-image ordering and SKU snapshot.

Pending registration work is published with this merge: normalize identity fields, map duplicate identities to a useful conflict response, require role/profile persistence, compensate only newly created Keycloak identities if registration fails, and retain the project's optional email verification setting (disabled by default).

Controllers, service interfaces/implementations, DTO/MapStruct structure and OpenFeign clients are preserved. Modified Java files were formatted with IntelliJ.

## Validation

All module test phases passed across the initial reactor and the resumed run after conflict resolution:

| Module | Tests | Failures / errors | Skipped |
| --- | ---: | ---: | ---: |
| User | 39 | 0 / 0 | 0 |
| Gateway | No test sources | — | — |
| Product | 83 | 0 / 0 | 8 |
| Order | 76 | 0 / 0 | 1 |
| Payment | 64 | 0 / 0 | 2 |
| Promotion / Affiliate | 92 | 0 / 0 | 2 |

Total: 354 test cases, 341 executed successfully, 13 skipped. The optional PostgreSQL migration/concurrency suites require isolated QA database environment variables; seven of Product's skips are the existing Sora catalog/chat PostgreSQL suite. Application databases were not reset or used for destructive test setup.

The companion FE validation report covers the five-role browser navigation matrix, registration, session privacy and the seven pre-existing develop Node test failures. Fixtures do not establish production payment settlement or high-concurrency livestream capacity.

Private `.env` files remain outside Git. Restart services to load new classes; running IntelliJ processes were not stopped. No pull request is created or merged by this task.
