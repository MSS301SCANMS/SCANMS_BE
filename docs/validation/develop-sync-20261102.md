# Develop synchronization — 2026-11-02

Updated `feat/livestream-commerce-sync-20261027` from `origin/develop` at `e851437` by fast-forward; no Git conflicts.

Incoming interface/implementation extraction had omitted behavior already present on this feature branch. Restored the behavior inside the new service boundary:

- Inventory reservation DTOs carry buyer subject and live purchase proof. The implementation preserves live offer pricing/quotas, expired session rejection, captured listed and sale amounts, SKU/image snapshots, idempotent replay after product hiding, and live quota release.
- Voucher checkout preserves one discount plus one shipping voucher, per-store shipping fees, redemption restoration/cancellation, and the existing shipping header contract.
- Order exposes commission adjustment basis; Affiliate preserves product deal commission snapshots, reconciliation basis/acknowledgement, and reversal handling.
- Existing regression tests use the extracted DTOs and manager decision type.
- Address book tests explicitly use H2 dialect with their H2 datasource; production PostgreSQL configuration is unchanged.

Validation:

- All eight Java reactor modules package successfully, including test compilation.
- Inventory, live commerce/lifecycle, commerce lifecycle, return reservations, cancellation, and checkout voucher regression selection: 57 cases, 55 passed, 2 optional skipped.
- The initial full test run stopped at User Service with three H2 JSON mapping failures. After the test-only dialect correction, the separate address book rerun passed all 3 tests. Total focused BE validation: 58 passed, 2 optional skipped.
- FE build and 17 selected FE tests pass; details are in the FE validation note.
- Changed files passed the local known-secret scan.

Tests use isolated in-memory databases. No application database migrations, seeds, or runtime service restarts were performed. The incoming Python AI feature was not exercised end to end.
