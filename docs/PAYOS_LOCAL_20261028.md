# PayOS local channel

Payment Service already owns payment links, signed webhooks, bank status queries and the payment-to-Order outbox. FE uses Gateway finance APIs and never receives provider keys.

## Backend configuration

Set these values only in ignored `payment-service/.env`:

```dotenv
PAYOS_CLIENT_ID=<channel client ID>
PAYOS_API_KEY=<channel API key>
PAYOS_CHECKSUM_KEY=<channel checksum key>
FRONTEND_URL=http://localhost:5173
```

Keep the current Payment database and encryption key unless intentionally migrating financial data. `FINANCE_DB_*` from a legacy bridge are not automatically Spring `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` settings. On 28 October the supplied port 55433 was unavailable, so no financial database migration or encryption-key replacement was performed. The existing Payment database on port 5435 remains in use. Existing Order/Payment/Affiliate technical identities were validated against local Keycloak and retain their configured internal roles.

Start Payment on 8084 and Gateway on 8080. Check `GET /api/v1/finance/capabilities` through Gateway: `paymentAvailable` should be true. This only reports configured keys; the live provider smoke test below additionally verified that PayOS accepts them. `sandboxTopUpAvailable` refers to the separate development wallet feature, not a simulated PayOS channel.

## HTTPS webhook

The Spring endpoint is **`/api/v1/payments/webhooks/payos`**. The old `/api/orders/payos/webhook` path is accepted by the narrow relay for compatibility, then forwarded to the selected money authority.

Install [cloudflared](https://developers.cloudflare.com/tunnel/get-started/) and run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Start-PayosWebhook.ps1 -CloudflaredPath <path-to-cloudflared.exe>
```

The helper requires Payment to be running and relay port 3301 to be free. It launches a relay bound to 127.0.0.1 and a Cloudflare Quick Tunnel, then calls PayOS's official signed webhook-registration probe. It stores the confirmed URL in the ignored Payment environment and writes process IDs to `.run-logs/payos-webhook-processes.json`. Only webhook paths are exposed by the relay; wallet/admin endpoints return 404. SPRING authority forwards a callback once to Payment and returns its status; it does not also call the old Node server.

Keep relay and tunnel processes alive while receiving payments. Closing them or restarting the machine invalidates the temporary tunnel. Rerun the helper after stopping the previously recorded relay/tunnel processes; it issues and registers a new URL. If a helper attempt fails, it stops only its own new processes. For a stable deployed environment, use a stable HTTPS hostname instead of a Quick Tunnel. See [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).

## Verification performed on 28 October

- Actual Java `PayosProviderImpl` created a 1,000 VND payment link with QR, queried PENDING and cancelled it successfully. No money was transferred.
- Gateway returned `paymentAvailable=true`; Payment started successfully with schema validation.
- PayOS `/confirm-webhook` returned success for the new tunnel and its signed probe was accepted by Payment. See [PayOS API](https://payos.vn/docs/api/).
- A public webhook request with an invalid signature returned HTTP 400.
- Relay regression tests passed: signed bytes preserved, one selected authority, no exposure of unrelated endpoints, invalid signatures remain rejected.
- Existing local QA login credentials were rejected, so the additional authenticated Gateway top-up test did not proceed or create a payment. A real customer checkout and bank transfer remain to be verified interactively.

The provider confirmation/webhook is authoritative. Returning from the PayOS page, editing a URL or seeing a QR does not mean an order is paid. Order becomes paid only after verified Payment confirmation and internal acknowledgement; duplicate webhooks remain idempotent.
