package com.accso.shipment.dto;

import com.accso.shipment.model.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Mirrors the brief's example schema.
 * <p>
 * v1 assumed every courier partner provides a stable, unique eventId per
 * update. CHANGE REQUEST: a partner turned out not to be able to guarantee
 * one, so eventId is now optional - when absent, DedupeKeyGenerator falls
 * back to a content-based key. See ADR-002's "Revisit" section.
 */
public record IngestEventRequest(
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
