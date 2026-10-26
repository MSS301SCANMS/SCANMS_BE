# Livestream schedule expiry

Product Service closes elapsed sessions automatically at startup (after 1 second)
and every 30 seconds. Reading the livestream list or detail also reconciles stale
states immediately. No browser needs to remain open for the scheduler to run.

The existing status contract stays unchanged:

| Before the scheduled end | At or after `participantRefs.endsAt` | Lifecycle reason |
| --- | --- | --- |
| SCHEDULED, never started | CANCELLED (UI: Đã hết lịch) | SCHEDULE_EXPIRED |
| LIVE, or already started | ENDED | TIME_LIMIT_REACHED |

The service records `endedAt` at the planned deadline, preserves invitation
decisions and references, and adds system closure metadata in
`participantRefs.lifecycle`. A manual cancellation keeps its original meaning.
Times with an offset are converted to UTC; suffixless LocalDateTime values use
the existing UTC API contract. An overnight session remains open until its real
deadline. Missing or malformed historical deadlines are left untouched.

Start/invitation/pinning, offers, viewer recording and new inventory checkout
validate expiry even between scheduler ticks. Existing checkout reservations
retain their original snapshots. The signaling relay also refuses elapsed
sessions and disconnects rooms after its next authorization refresh.

Merchant and KOL lists refresh every 15 seconds while visible. Merchant refreshes
on focus too, including an open detail dialog. Expired cards no longer show
pending invitations or a link to open the broadcast.

Configuration: `scanms.live.lifecycle.enabled` (default true),
`scanms.live.lifecycle.initial-delay-ms` (1000),
`scanms.live.lifecycle.delay-ms` (30000).

Validation covers exact end boundaries, UTC/offsets, overnight schedules,
idempotent transitions, list/detail persistence, blocked expired mutations and
checkout, relay room closure, and merchant UI with an open dialog.
Restart Product Service after updating the code to activate the scheduler.
