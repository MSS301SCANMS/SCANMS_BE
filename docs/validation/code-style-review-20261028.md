# Review of recent coding style

Reference changes: Thanhnha28's `23ccee8` (MapStruct, interface/impl and removal of fully qualified class names), `2cde24c` (format), and Thanh Nam's `c2e2a75` / `75673ab` (service boundaries, collaborator and livestream flow). This review covers the recent checkout/live/cancellation/search work, rather than imposing a new architecture on every legacy module.

## Confirmed conventions

- New business controllers delegate to service interfaces; implementations live under `service.impl`.
- Requests use DTOs and Bean Validation; HTTP responses use the module's `ApiResponse` convention. Create endpoints use the existing 201 pattern.
- New internal inventory, wallet, cancellation, affiliate and public sales clients use OpenFeign interfaces. Provider-specific HTTP and pre-existing finance `RestClient` code still exist; the whole repository has not been converted to Feign.
- Search and live response mappings use MapStruct. JSONB refs, saga snapshots and signed webhook maps retain their existing contracts; they are not replaced with unrelated persistence entities.
- Dependencies in new services use constructor injection. Existing optional injection/public catalog seams were preserved.

## Corrections

Normalized imports in 14 recently touched Java files, replacing inline fully qualified Java/Spring/Feign/domain class names, grouping imports and reformatting with IntelliJ. Fully qualified enum literals inside JPQL strings remain required query syntax.

FE review kept API calls in services, lifecycle logic in hooks and display in components. Search history now has explicit Promise result types and stable primitive authentication dependencies. Checkout quote callbacks use refs and destination values are memoized by their actual fields. Shop cancellation has a named Props interface and catches `unknown` errors through the existing API error wrapper. Compressed control-flow blocks were expanded.

## Evidence and limits

- Product/Order/Payment focused regression run: 48 executed tests passed, 2 optional cases skipped (50 cases).
- FE TypeScript/Vite build passes. Lint exits successfully with no errors; 85 warnings remain across the repository. The modified search, navigation, quote and cancellation files have no warnings in that run.
- Four explicit browser fixtures pass: search isolation/deduplication, checkout pricing/wallet/payment navigation, checkout Back and shop cancellation decisions.
- The seven previously documented regular FE suite failures remain an existing baseline; this style review does not claim a clean full FE suite.

Remaining legacy finance controllers/dispatchers contain business logic and `RestClient` calls. Converting those wholesale should be a separately scoped refactor with contract coverage, rather than a formatting-only change.
