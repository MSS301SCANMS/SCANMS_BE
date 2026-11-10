# Livestream duration and flexible start

Updated 2026-11-08: the shop may start an unstarted scheduled session before or
after its appointment, including after the original planned end. Store ownership,
approved products, accepted KOL invitations and partnership checks still apply.
Creation/rescheduling still requires a valid future appointment.

`scheduledAt` remains the appointment. At start the service saves actual
`startedAt`, retains the previous end in `participantRefs.scheduledEndsAt`, and
sets `participantRefs.endsAt` to actual start plus the original planned duration.
For example, an appointment of 20:00–21:00 started at 20:30 runs until 21:30.
Starting again is rejected and does not extend the broadcast. An unscheduled
legacy session with only an end keeps that deadline; one with no end retains its
existing behavior. No schema migration or bulk update of existing sessions runs.

Product Service closes elapsed sessions automatically at startup (after 1 second)
and every 30 seconds. Reading the livestream list or detail also reconciles stale
states immediately. No browser needs to remain open for the scheduler to run.

The existing status contract stays unchanged:

| Before the scheduled end | At or after `participantRefs.endsAt` | Lifecycle reason |
| --- | --- | --- |
| SCHEDULED, never started | SCHEDULED, waiting for the shop | none |
| LIVE | ENDED at the effective end set on start | TIME_LIMIT_REACHED |

The service records `endedAt` at the planned deadline, preserves invitation
decisions and references, and adds system closure metadata in
`participantRefs.lifecycle`. A manual cancellation keeps its original meaning.
Times with an offset are converted to UTC; suffixless LocalDateTime values use
the existing UTC API contract. An overnight session remains open until its real
deadline. Missing or malformed historical deadlines are left untouched.

Pinning, offers, viewer recording and new inventory checkout validate expiry for
running broadcasts even between scheduler ticks. Existing checkout reservations
retain their original snapshots. The signaling relay permits a scheduled waiting
room after its appointment, blocks publishing before shop start, and closes an
elapsed running room after its next authorization refresh. Voucher and offer
validity windows are not extended when the livestream starts late.

Merchant and KOL lists refresh every 15 seconds while visible. Merchant refreshes
on focus too, including an open detail dialog. Scheduled cards keep their start
action when KOL consent is complete. Actual start/end times appear after start,
and broadcast notices use the new server-confirmed end. Existing ENDED/CANCELLED
records, including records previously cancelled by schedule expiry, are not
automatically reopened by this change.

Configuration: `scanms.live.lifecycle.enabled` (default true),
`scanms.live.lifecycle.initial-delay-ms` (1000),
`scanms.live.lifecycle.delay-ms` (30000).

Validation on 2026-11-08: 25 targeted Product tests, 6 relay tests, 13 FE tests,
and the shop browser test `live-flexible-start.browser.test.mjs` passed. These
cover early/late starts, preserved duration and appointments, read/sweep behavior,
ownership/consent, no reopening closed records, blocked expired LIVE checkout,
relay waiting/publishing/closure, and notices using the new deadline.
Restart Product Service and the signaling relay after updating the code.
