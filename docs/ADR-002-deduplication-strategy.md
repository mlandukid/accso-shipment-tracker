# ADR-002: Deduplication via a precomputed key + database unique constraint

## Status
Accepted (extended, not replaced, by the change request - see the "Revisit" section at the bottom)

## Context

The brief explicitly calls out duplicate detection as a priority, and gives a pointed example of
what "good AI usage" looks like: rejecting an in-memory deduplication approach because it
wouldn't survive restarts, in favor of a database constraint. That's a real design decision this
service needs to make correctly, not just cite.

## Decision

Compute one normalized `dedupeKey` string per inbound event, application-side, before doing
anything else with it. In v1, with every partner assumed to supply a stable `eventId`, the key is
simply `partner + "::" + eventId` (partner-scoped so two couriers reusing the same id scheme don't
collide). Store it in a dedicated column with a **database-level unique constraint**. On ingest:

1. Fast-path check: `existsByDedupeKey(key)` - if true, reject as duplicate without touching the
   event log.
2. Otherwise, insert. If the insert itself throws a unique-constraint violation (two identical
   requests raced each other past step 1), catch it and treat that as a duplicate too.

The constraint, not the pre-check, is the actual guarantee. The pre-check just avoids paying for
an exception in the common case.

## Alternatives considered

- **In-memory `Set`/cache of seen event ids.** This is close to what an AI assistant suggested
  first when this was prototyped (see `AI_PROCESS.md`) - fast to write, and it "worked" in a quick
  test. Rejected for the reason the brief itself flags: it doesn't survive a restart, and it
  doesn't work at all across multiple service instances, which a real deployment would have. A
  duplicate arriving five minutes after a redeploy would silently corrupt the audit trail.
- **Relying on a plain (non-generated) unique column for `eventId` alone**, without a separate
  computed key. Rejected because it doesn't generalize: it assumes every partner can always supply
  one, and it says nothing about *how* to detect a duplicate for a partner that can't. Baking that
  assumption directly into the schema would have made the change request a schema migration
  instead of a one-method change - see below.
- **Per-shipment advisory/pessimistic locks** to fully eliminate the ingest-time race described in
  step 2 above. Not implemented - the DB constraint already makes the outcome *correct* under a
  race (one request wins, the other is correctly told "duplicate"), it just means the loser pays
  for a caught exception instead of a cheap pre-check. At this scale that trade-off is fine;
  flagged as a limitation if this needs to handle high-concurrency bursts per shipment in
  production.

## Consequences

- Deduplication and the "preserve an audit trail of accepted events" requirement are the *same*
  mechanism: nothing that fails the uniqueness check is ever written, so the event table is
  automatically a clean accepted-only log with no separate "mark as duplicate and keep anyway"
  bookkeeping needed.
- The one place that would need to change to support a courier with different idempotency
  semantics is `DedupeKeyGenerator` - everything downstream (the constraint, the repository, the
  service's duplicate-handling branch) is agnostic to how the key was built.

## Revisit: the change request

A new partner can't provide a stable `eventId` and resends the same update, sometimes with a
different `receivedAt`. `DedupeKeyGenerator` now branches: if `eventId` is present, same rule as
before; if absent, the key is a content hash of `partner + shipmentId + status + occurredAt +
location` (location normalized for case/whitespace), deliberately excluding `receivedAt` (the
field the partner varies) and `eventId` (absent). No schema change, no change anywhere else in the
system - see the main README's "Change request" section and `AI_PROCESS.md` for how that played
out and what it would still need for production.
