package com.accso.shipment.service;

import com.accso.shipment.dto.IngestEventRequest;
import com.accso.shipment.model.EventStatus;
import com.accso.shipment.model.IngestOutcome;
import com.accso.shipment.model.ShipmentEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The "hard part" of this assignment, deliberately isolated from Spring and
 * the database so it can be unit tested as plain logic. Given a shipment's
 * already-accepted events and a new candidate event, decides:
 *   - what the shipment's current state is (max occurredAt, ties broken by
 *     status precedence), and
 *   - how a new event should be classified against that current state.
 *
 * This class contains no I/O and mirrors, field for field, a standalone
 * prototype that was run and verified (15/15 scenarios) before being wired
 * into Spring - see AI_PROCESS.md.
 */
public final class ShipmentStateResolver {

    private ShipmentStateResolver() {
    }

    /** Current state = the accepted event with the latest occurredAt; ties broken by status precedence. */
    public static Optional<ShipmentEvent> resolveCurrent(List<ShipmentEvent> acceptedEvents) {
        return acceptedEvents.stream().max(
                Comparator.comparing(ShipmentEvent::getOccurredAt)
                        .thenComparingInt(e -> e.getStatus().getPrecedence())
        );
    }

    /** Classifies a new (already known non-duplicate) event against the shipment's current state. */
    public static IngestOutcome classify(IngestEventRequest request, Optional<ShipmentEvent> currentMax) {
        if (currentMax.isEmpty()) {
            return IngestOutcome.ACCEPTED_STATE_CHANGED;
        }
        int cmp = request.occurredAt().compareTo(currentMax.get().getOccurredAt());
        if (cmp > 0) {
            return IngestOutcome.ACCEPTED_STATE_CHANGED;
        }
        if (cmp < 0) {
            return IngestOutcome.ACCEPTED_NO_STATE_CHANGE;
        }
        EventStatus currentStatus = currentMax.get().getStatus();
        return request.status() == currentStatus
                ? IngestOutcome.ACCEPTED_NO_STATE_CHANGE
                : IngestOutcome.ACCEPTED_CONFLICT;
    }

    public static boolean hasConflict(List<ShipmentEvent> acceptedEvents) {
        return acceptedEvents.stream().anyMatch(e -> e.getOutcome() == IngestOutcome.ACCEPTED_CONFLICT);
    }
}
