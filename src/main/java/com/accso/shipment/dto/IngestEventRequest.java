package com.accso.shipment.dto;

import com.accso.shipment.model.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Mirrors the brief's example schema.
 * <p>
 * ASSUMPTION (v1): every courier partner provides a stable, unique eventId
 * per shipment update - this is what the example payload in the brief
 * implies, and it's the simplest thing that could be true. eventId is
 * therefore required. Revisited in the change request, where a partner
 * turns out not to be able to provide one.
 */
public record IngestEventRequest(
        @NotBlank(message = "eventId is required")
        String eventId,

        @NotBlank(message = "partner is required")
        String partner,

        @NotBlank(message = "shipmentId is required")
        String shipmentId,

        @NotNull(message = "status is required")
        EventStatus status,

        @NotNull(message = "occurredAt is required")
        Instant occurredAt,

        /** Optional - if the courier omits it, we stamp server-received time instead. */
        Instant receivedAt,

        String location
) {
}
