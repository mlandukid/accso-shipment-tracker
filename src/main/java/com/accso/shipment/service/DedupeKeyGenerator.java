package com.accso.shipment.service;

import com.accso.shipment.dto.IngestEventRequest;
import org.springframework.stereotype.Component;

/**
 * Computes one normalized idempotency key per inbound event. See ADR-002.
 *
 * <p>v1: every partner provides a stable eventId (required, see
 * IngestEventRequest), so the key is simply {@code partner::eventId} -
 * scoped by partner so two couriers reusing the same id scheme don't
 * collide.
 */
@Component
public class DedupeKeyGenerator {

    public String generate(IngestEventRequest request) {
        return request.partner() + "::" + request.eventId();
    }
}
