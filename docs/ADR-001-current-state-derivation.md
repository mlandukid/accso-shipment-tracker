# ADR-001: Derive current shipment state on read, don't maintain a separate mutable record

## Status
Accepted

## Context

The service needs to answer two questions that must never disagree with each other:

1. What's the current status of shipment X? (`GET /shipments/{id}`)
2. What's the full history of events for shipment X? (`GET /shipments/{id}/events`)

A natural first instinct is to keep two tables: an append-only `shipment_events` log for history,
and a `shipments` table holding one row per shipment that gets UPDATEd every time a new event
comes in. That's a common pattern, and it makes reads of "current state" O(1).

## Decision

Don't maintain a separate `shipments` table. Store only the append-only event log. Current state
is computed on read: the accepted event with the maximum `occurredAt` for that shipment (ties
broken by a fixed status-precedence ranking - see `EventStatus` and ADR discussion in the main
README's Data Integrity Rules table).

## Alternatives considered

- **A mutable `shipments` table, updated transactionally alongside every insert into the event
  log.** Rejected: this is a dual-write. Every place that decides "does this new event become the
  current state" has to be kept in perfect sync between the write path (which updates the
  `shipments` row) and the read path (which trusts that row). Any bug, migration, or future
  change to the classification rule creates a window where the two disagree - and the "shipment
  disagree about status" problem this whole assignment exists to solve would exist *inside the
  service itself*.
- **Full event sourcing with periodic snapshotting.** Considered for scale, rejected as
  over-engineering for the stated scope ("don't aim for a large platform"). Nothing here needs
  snapshotting yet - the read query is a single indexed lookup plus an in-memory `max()` over
  what's realistically a handful to low hundreds of events per shipment.

## Consequences

- **Correctness is structural, not procedural.** There's exactly one function
  (`ShipmentStateResolver.resolveCurrent`) that defines what "current state" means, and it's used
  by both the state endpoint and (indirectly, for conflict detection) the history endpoint. It's
  impossible for the two to drift, because there's only one source of truth.
- **Read cost is slightly higher** than a pre-computed row: each `GET /shipments/{id}` does a
  `WHERE shipment_id = ?` query (indexed) plus a small in-memory scan. At the scale implied by the
  brief this is a non-issue. If it ever became one, the fix is additive, not a redesign: add a
  materialized projection table populated by the exact same resolver logic, keep the same
  read API on top of it, and nothing downstream needs to change.
- **Out-of-order events fall out of the design for free.** Because "current state" is a query, not
  a running value someone forgot to guard with a comparison, an event arriving late simply loses
  the `max()` comparison. There's no special "did this arrive out of order, and if so what do I do
  about the record I already wrote" branch to get wrong.
