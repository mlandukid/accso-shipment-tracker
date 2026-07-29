# Shipment Tracker

A focused backend service that gives internal systems (support, tracking, incident response) a
reliable current view of each shipment, and a full queryable history of how it got there - even
when courier webhook events arrive late, out of order, duplicated, or conflicting.

Built for the Accso technical assignment.

## Problem framing

The brief is explicit that this should not be a large platform, and that duplicates,
out-of-order events, and conflicting updates matter more than feature breadth. So the scope here
is deliberately narrow:

- One aggregate (a shipment, identified by `shipmentId`), one event type (a status update).
- No shipment "creation" endpoint - a shipment implicitly exists once its first event is ingested.
  This matches how courier webhooks actually behave: nobody calls a "create shipment" API before
  the first webhook fires.
- No auth, no multi-tenant partner management, no retry/backoff for webhook delivery, no
  notification fan-out. Those are real e-commerce-platform concerns, but they're not what this
  brief is testing.
- The two things it *does* need to be excellent at: (1) never lose an event, and (2) never let a
  duplicate or stale event corrupt "what's the status right now".

## Assumptions

1. **Every courier partner provides a stable, unique `eventId` per update.** This is what the
   brief's example payload implies, and it's the simplest thing that could be true. (Revisited by
   the change request - see below.)
2. **`occurredAt` is business time and is trustworthy.** It's courier-supplied, but it's the only
   signal we have for "when did this actually happen", and the brief explicitly asks for current
   state to be reported by `occurredAt`, not `receivedAt`. If a partner's clock is unreliable in
   production, that's a data-quality problem to solve upstream (partner integration testing), not
   something this service can fully protect against.
3. **`receivedAt` is optional in the request.** If a courier doesn't send it, the server stamps
   its own receipt time. It's kept for audit/arrival-order forensics only - it never drives
   current-state logic.
4. **A shipment is scoped to a single logical entity across partners.** If two different couriers
   report events for the same `shipmentId` (e.g. a carrier handoff), both are treated as
   authoritative signals about the same shipment - the model doesn't currently distinguish "the
   courier of record" from others. Flagged as a limitation below.
5. **No shipment ever needs to be deleted or corrected out-of-band.** Everything is
   append-only. If a real correction mechanism is needed (e.g. a courier says "actually, ignore
   event X"), that's a new event type this design doesn't yet have.

## How to run

Requires Java 21 and Maven (no Docker needed).

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8080`. It uses a file-based H2 database
(`./data/shipment-tracker`), so state survives restarts - this matters because the dedup
guarantee is a real DB constraint, not an in-memory set that would reset on every restart (see
ADR-002 and AI_PROCESS.md for why that distinction mattered).

Run the tests:

```bash
mvn test
```

## API

### `POST /shipment-events`

Ingest a courier webhook event.

```json
{
  "eventId": "evt-123",
  "partner": "dhl",
  "shipmentId": "ship-456",
  "status": "IN_TRANSIT",
  "occurredAt": "2026-03-10T12:00:00Z",
  "receivedAt": "2026-03-10T12:00:05Z",
  "location": "Amsterdam"
}
```

Response:

```json
{
  "outcome": "ACCEPTED_STATE_CHANGED",
  "shipmentId": "ship-456",
  "eventId": "evt-123",
  "message": "Event accepted and is now the current state for this shipment."
}
```

`outcome` is one of:

| Outcome | Meaning | HTTP status |
|---|---|---|
| `ACCEPTED_STATE_CHANGED` | New/newer event - it's now the current state | 201 |
| `ACCEPTED_NO_STATE_CHANGE` | Valid event, but older than what we already have (out-of-order) - stored, doesn't change current state | 201 |
| `ACCEPTED_CONFLICT` | Same `occurredAt` as the current state, different status - stored and flagged, tie broken by status precedence | 201 |
| `REJECTED_DUPLICATE` | Already processed this exact event - not stored again | 200 |

### `GET /shipments/{shipmentId}`

```json
{
  "shipmentId": "ship-456",
  "currentStatus": "IN_TRANSIT",
  "statusOccurredAt": "2026-03-10T12:00:00Z",
  "eventsProcessed": 3,
  "explanation": "Reflects the accepted event with the latest occurredAt (2026-03-10T12:00:00Z) out of 3 accepted event(s) for this shipment."
}
```

404 if no events have ever been accepted for that shipment.

### `GET /shipments/{shipmentId}/events`

Full event history, ordered by `occurredAt` descending (business time - see "Data integrity
rules" below for why). Every event carries the `outcome` it was classified with at ingest time,
so it's immediately visible which event set the current state, which were out-of-order, and which
conflicted.

```json
{
  "shipmentId": "ship-456",
  "orderedBy": "occurredAt DESC (business time, not arrival time)",
  "events": [
    { "eventId": "evt-2", "partner": "dhl", "status": "IN_TRANSIT", "occurredAt": "...", "receivedAt": "...", "location": "Amsterdam", "outcome": "ACCEPTED_STATE_CHANGED" },
    { "eventId": "evt-1", "partner": "dhl", "status": "LABEL_CREATED", "occurredAt": "...", "receivedAt": "...", "location": "Berlin", "outcome": "ACCEPTED_STATE_CHANGED" }
  ]
}
```

## Data integrity rules

| Case | Rule | Why |
|---|---|---|
| **Duplicate** | Same `partner` + `eventId` seen before → rejected, not stored again. Enforced by a DB unique constraint on a precomputed `dedupeKey` column, not an in-memory check. | Survives restarts, works correctly under concurrent requests (the constraint is the real guarantee; the pre-check is just a fast path to avoid a wasted write/exception in the common case). |
| **Out-of-order** | Event's `occurredAt` is older than the shipment's current max → still stored (full audit trail), but does **not** change current state. Classified as `ACCEPTED_NO_STATE_CHANGE`. | Current state is always defined as "the event with the latest occurredAt we've seen" - so out-of-order arrival is handled by the definition itself, no special-case reordering logic needed. |
| **Conflicting update** | Two events share the exact same `occurredAt` but report different statuses → both stored, the second is classified `ACCEPTED_CONFLICT`, and the winner is picked via a fixed status-precedence ranking (see `EventStatus`). The shipment view surfaces a note when this has happened. | There's no way to know which of two simultaneous, contradictory reports is "more correct" without more information. Precedence is a documented, deterministic tie-break, not a claim that it's always the right business answer - flagged as a known limitation. |

## Design choices and trade-offs

The two most significant decisions are written up as ADRs in `docs/`:

- **`docs/ADR-001-current-state-derivation.md`** - why current state is computed on read from the
  event log, instead of maintained as a separately-updated "shipment" row.
- **`docs/ADR-002-deduplication-strategy.md`** - why deduplication is a precomputed key + DB unique
  constraint, and how that same mechanism is what let the change request drop in cleanly.

Other choices worth calling out:

- **HTTP status codes**: `201` for anything newly persisted (even `ACCEPTED_NO_STATE_CHANGE` - the
  audit record is genuinely new), `200` for a duplicate (idempotent no-op, not an error). No `409`
  - a duplicate isn't a conflict from the caller's point of view, it's success (the event is, and
    remains, recorded).
- **History ordering by `occurredAt`, not `receivedAt`**: the brief asks for current state by
  `occurredAt`; the history endpoint uses the same field for consistency; `receivedAt` is
  preserved per-event for anyone debugging arrival-order/latency issues.

## Known limitations

- **Status-precedence tie-break is a guess, not a confirmed business rule.** In production, "which
  status wins when two couriers disagree" is a product decision, ideally informed by which courier
  is authoritative for that leg of the journey. Right now every event is treated as equally
  trustworthy.
- **No idea of "courier of record".** If two different partners report conflicting events for the
  same shipment, the model has no notion of which one should actually be trusted more.
- **No replay/correction event type.** If a courier needs to retract or amend a past report, there's
  no first-class way to express that today - it would show up as just another (possibly
  conflicting) event.
- **Content-hash dedup (added for the change request, see below) is an approximation, not a true
  idempotency key.** See that section for specifics.
- No auth, no rate limiting, no pagination on the history endpoint (fine at this scale, would
  matter for a shipment with thousands of events).

## Change request

See the second commit in this repo and the "Change request" section further down / `docs/AI_PROCESS.md`
for what changed when a new partner turned out not to be able to provide a stable `eventId`.

## Testing

- `ShipmentStateResolverTest` - the decision logic (current-state resolution, out-of-order
  handling, conflict tie-break), no Spring context, fast.
- `DedupeKeyGeneratorTest` - dedupe key generation rules.
- `ShipmentEventIntegrationTest` - full HTTP flow through a real Spring context and H2 database:
  ingest → query current state → query history, plus duplicate rejection, 404, and validation.
- `ChangeRequestNoStableEventIdTest` (added in the second commit) - the change request scenario
  specifically.

## AI development process

See `docs/AI_PROCESS.md` for how AI tooling was used while building this, including a concrete
example of where an AI-suggested approach was rejected and why.
