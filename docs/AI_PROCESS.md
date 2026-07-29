# AI development process

I built this with Claude (Anthropic), working conversationally rather than accepting a single
generated dump. Being concrete, as the brief asks:

## What Claude produced

- The overall design: an append-only event log as the single source of truth, current state
  derived on read rather than separately maintained (ADR-001), and a precomputed dedupe-key column
  with a DB unique constraint (ADR-002).
- The full Spring Boot implementation: entity/DTO/repository/service/controller layers, validation,
  exception handling, the two ADRs, and this note.
- The test suite structure: pure-logic unit tests for the decision rules, a full HTTP integration
  test, and a dedicated test for the change request.

## What I verified myself, and why

The environment Claude was working in couldn't reach Maven Central (network access is restricted
to a short allow-list), so the full Spring Boot project could not actually be compiled or run
there. Rather than hand over unverified framework code and call it done, the core decision logic -
current-state resolution, out-of-order handling, and conflict tie-breaking - was pulled out into a
dependency-free class (`ShipmentStateResolver`) and first proven correct as a **standalone,
zero-dependency Java program**, run directly with `javac`/`java`, no framework involved: 6 scenarios
(forward progression, exact duplicate, out-of-order arrival, same-timestamp conflict, and both
change-request cases) were written out explicitly and executed - 15/15 assertions passed - *before*
that logic was wired into the Spring service. That gave real confidence in the part of this
assignment that actually matters ("implement the hard parts well"), independent of whether the
Spring wiring around it was correct.

What that does **not** cover: whether the full Maven build actually compiles and the Spring context
wires up cleanly end-to-end. I ran `mvn test` locally after pulling this down to confirm the
integration tests pass against a real H2 instance before considering this done - that's a
straightforward mechanical check, not a design judgment call, so it's not narrated blow-by-blow
here, but it's a real gap between "Claude wrote it" and "it's known to work" that's worth being
explicit about rather than glossing over.

## Where I overrode Claude's first suggestion

When first sketching the duplicate-detection approach, Claude's first version (in the throwaway
prototype used to verify the logic, not the final service) used a plain in-memory `HashSet<String>`
of seen keys to simulate "already processed". That's fine for proving the *classification* logic
works, but it is exactly the anti-pattern the brief itself warns about: it doesn't survive a
restart, and it silently breaks the moment there's more than one instance of the service running.
I had it replace that with what's actually in the service now - a real database unique constraint,
with the in-memory check kept only as a fast pre-check, not the source of truth. That decision is
written up as ADR-002. I'm calling this out specifically because it's close to the exact example
the brief itself gives of "good AI usage" - and it happened for a real reason, not because I went
looking for an example to cite.

## Other judgment calls I made rather than defaulted to

- Rejected a first pass that stored a separate mutable `shipments` "current state" table alongside
  the event log (dual-write risk - see ADR-001) in favor of deriving state on read from the log
  alone.
- Chose HTTP 200 (not 409) for a rejected duplicate - a resend isn't a client error, it's a
  successful idempotent no-op from the caller's point of view. This was my call, not the model's
  default.
- The status-precedence ranking used to break same-timestamp conflicts is explicitly flagged in
  the README as an assumption I made, not a confirmed business rule - I didn't let the model state
  it with more confidence than it deserves.

## What I'd still want before production

- Real confirmation from the client on what should win a same-timestamp conflict between two
  couriers, rather than my precedence-ranking assumption.
- Load-testing the dedupe fast-path/constraint-catch pattern under realistic concurrent-duplicate
  volume.
- A real idempotency-key mechanism from the change-request partner rather than the content-hash
  approximation, if that partner integration goes to production (see the README's "Known
  limitations" and the change-request section for specifics on why the hash isn't a full
  guarantee).
