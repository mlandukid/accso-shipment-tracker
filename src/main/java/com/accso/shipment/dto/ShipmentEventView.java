package com.accso.shipment.dto;

import com.accso.shipment.model.EventStatus;
import com.accso.shipment.model.IngestOutcome;
import com.accso.shipment.model.ShipmentEvent;

import java.time.Instant;

public record ShipmentEventView(
        String eventId,
        String partner,
        EventStatus status,
        Instant occurredAt,
        Instant receivedAt,
        String location,
        IngestOutcome outcome
) {
    public static ShipmentEventView from(ShipmentEvent e) {
        return new ShipmentEventView(
                e.getEventId(), e.getPartner(), e.getStatus(),
                e.getOccurredAt(), e.getReceivedAt(), e.getLocation(), e.getOutcome()
        );
    }
}
