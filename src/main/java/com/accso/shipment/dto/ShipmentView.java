package com.accso.shipment.dto;

import com.accso.shipment.model.EventStatus;

import java.time.Instant;

public record ShipmentView(
        String shipmentId,
        EventStatus currentStatus,
        Instant statusOccurredAt,
        int eventsProcessed,
        String explanation
) {
}
