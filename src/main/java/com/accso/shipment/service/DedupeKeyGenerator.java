package com.accso.shipment.service;

import com.accso.shipment.dto.IngestEventRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Computes one normalized idempotency key per inbound event. See ADR-002.
 *
 * <p>Rule 1 (v1, unchanged): the courier gives us a stable eventId - key is
 * {@code partner::eventId}, scoped by partner so two couriers reusing the
 * same id scheme don't collide.
 *
 * <p>Rule 2 (CHANGE REQUEST): a partner cannot provide a stable eventId and
 * resends the same update, sometimes with a different receivedAt. The key
 * falls back to a content hash of the fields that describe "the same
 * update" - partner, shipmentId, status, occurredAt, location - and
 * deliberately EXCLUDES receivedAt (which the partner varies on resend) and
 * eventId (absent). Two resends of an identical update collapse to the same
 * key; a genuinely new update (different status and/or occurredAt) does
 * not.
 *
 * <p>This is a content-hash approximation, not a true idempotency
 * guarantee - see the README's "Known limitations" for where it can still
 * go wrong in production (e.g. a legitimate re-report of the exact same
 * status at a slightly different but semantically-identical occurredAt
 * would be missed as a duplicate and accepted as a new event).
 */
@Component
public class DedupeKeyGenerator {

    public String generate(IngestEventRequest request) {
        if (StringUtils.hasText(request.eventId())) {
            return request.partner() + "::" + request.eventId();
        }
        String normalizedLocation = request.location() == null
                ? ""
                : request.location().trim().toLowerCase();
        return request.partner() + "::" + request.shipmentId() + "::"
                + request.status() + "::" + request.occurredAt() + "::" + normalizedLocation;
    }
}
