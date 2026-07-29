package com.accso.shipment.dto;

import com.accso.shipment.model.IngestOutcome;

public record IngestEventResponse(
        IngestOutcome outcome,
        String shipmentId,
        String eventId,
        String message
) {
    public static IngestEventResponse of(IngestOutcome outcome, IngestEventRequest request) {
        return new IngestEventResponse(outcome, request.shipmentId(), request.eventId(), describe(outcome));
    }

    private static String describe(IngestOutcome outcome) {
        return switch (outcome) {
            case ACCEPTED_STATE_CHANGED -> "Event accepted and is now the current state for this shipment.";
            case ACCEPTED_NO_STATE_CHANGE -> "Event accepted and stored for the audit trail, but it is older than the current state (out-of-order) so the current state is unchanged.";
            case ACCEPTED_CONFLICT -> "Event accepted, but it conflicts with another event at the same occurredAt timestamp. Resolved via status precedence - see GET /shipments/{id} for the winner.";
            case REJECTED_DUPLICATE -> "Duplicate event - already processed, not stored again.";
        };
    }
}
