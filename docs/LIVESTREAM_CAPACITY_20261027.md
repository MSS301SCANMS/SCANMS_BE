# Camera, audience and capacity

## Current implementation

The relay is a single Socket.IO process. Each KOL browser opens a separate WebRTC
peer connection to every subscriber, including the shop moderator. Video does
not pass through Socket.IO. The role determines permissions; receiving video
must not be restricted to the `viewer` role. Only the actual room host may send
an SDP offer; customers and moderators can answer that host.

The eye counter represents concurrent authenticated receiving accounts, including
the shop moderator. Multiple tabs consume separate media connections but count
once per subject. The publisher's subject is excluded, including any duplicate
viewer tabs belonging to that publisher. The counter is room presence, not proof of
decoded frames. It is distinct from accumulated visits, unique session viewers
and peak metrics stored by Product.

Controls in this version:

- Positive configurable media subscription limit, default 20, including the shop.
- At most 32 join authorization flows at once; excess attempts receive ROOM_BUSY
  with a retry delay. Full rooms return ROOM_FULL and admit no extra media peer.
- Counter broadcasts coalesced over 100 ms. Peak writes batched by the room poll.
- View event recording has one flush in flight and at most 4 HTTP calls at once.
- Media reconnection is independent of chat reconnection and is bounded/retryable.

These are protective limits, not measured capacity guarantees. The automated
burst checks admit only the configured number and verify deduplication and
bounded API concurrency. They do not measure Internet video quality at scale.

## Deployment for larger audiences

Use an SFU such as LiveKit: KOL publishes once (or a few simulcast layers), the
media server forwards tracks to subscribers. Server-side authorization issues
short-lived room-scoped tokens: publish for the accepted KOL, subscribe for
customers/shop. Preserve the existing Product/Store ownership and LIVE checks.
Provision HTTPS/WSS and TURN for restrictive networks. TURN assists connectivity;
it does not remove the per-viewer upload burden of the present P2P topology.

For very large audiences that tolerate more latency, add HLS/low-latency HLS
distribution through a CDN, keeping chat, voucher drops and product pinning on
the realtime channel. Choose media capacity using actual bitrate, CPU, outbound
bandwidth and latency measurements; do not raise MAX_VIEWERS_PER_ROOM blindly.

For multiple realtime nodes, move authoritative room membership, host ownership,
admission counters and presence leases to shared storage (e.g. Redis). Use a
Socket.IO Redis adapter for cross-node events and sticky sessions when polling
is enabled. An adapter alone does not synchronize this relay's in-memory Maps.
Use account-based leases/heartbeats to expire disconnected viewers and aggregate
metrics asynchronously with idempotency. Product's current per-visit JSONB
history takes a row lock and grows with the session, so it also needs a bounded
aggregation design before high traffic.

Heart counts are relay-authoritative and broadcast as absolute totals, including
join snapshots. Only accepted hearts increment; retries reuse an event ID.
Product persists the monotonic total in statistics.likeCount through a signed
relay endpoint. Writes are coalesced; abrupt failure before a successful flush
can lose the pending portion. Multiple relay processes need a shared atomic
counter and shared deduplication, rather than independent in-memory totals.

SFU/CDN/Redis/TURN infrastructure is a deployment step; this patch does not
configure a cloud service or claim production load capacity.

Sources: [LiveKit SFU](https://docs.livekit.io/reference/internals/livekit-sfu/),
[WebRTC TURN](https://webrtc.org/getting-started/turn-server),
[Socket.IO Redis adapter](https://socket.io/docs/v4/redis-adapter).
