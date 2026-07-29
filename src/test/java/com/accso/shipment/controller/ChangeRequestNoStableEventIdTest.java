package com.accso.shipment.controller;

import com.accso.shipment.dto.IngestEventRequest;
import com.accso.shipment.dto.IngestEventResponse;
import com.accso.shipment.model.EventStatus;
import com.accso.shipment.model.IngestOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Change request: "swifthaul" cannot provide a stable eventId and resends
 * the same update, sometimes with a different receivedAt. The service must
 * still reject the obvious duplicate while accepting a genuinely new update.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChangeRequestNoStableEventIdTest {

    @Autowired
    com.accso.shipment.service.ShipmentEventService service;

    @Test
    void resendWithDifferentReceivedAtOnlyIsRejectedAsDuplicate() {
        Instant occurredAt = Instant.parse("2026-03-10T09:00:00Z");

        IngestEventResponse first = service.ingest(new IngestEventRequest(
                null, "swifthaul", "ship-cr-1", EventStatus.OUT_FOR_DELIVERY, occurredAt, occurredAt, "Rotterdam"));
        IngestEventResponse resend = service.ingest(new IngestEventRequest(
                null, "swifthaul", "ship-cr-1", EventStatus.OUT_FOR_DELIVERY, occurredAt, occurredAt.plusSeconds(120), "Rotterdam"));

        assertThat(first.outcome()).isEqualTo(IngestOutcome.ACCEPTED_STATE_CHANGED);
        assertThat(resend.outcome()).isEqualTo(IngestOutcome.REJECTED_DUPLICATE);
        assertThat(service.getShipment("ship-cr-1").eventsProcessed()).isEqualTo(1);
    }

    @Test
    void genuinelyNewUpdateFromNoEventIdPartnerIsAccepted() {
        Instant occurredAt = Instant.parse("2026-03-10T09:00:00Z");
        Instant later = occurredAt.plusSeconds(3600);

        service.ingest(new IngestEventRequest(
                null, "swifthaul", "ship-cr-2", EventStatus.OUT_FOR_DELIVERY, occurredAt, occurredAt, "Rotterdam"));
        IngestEventResponse delivered = service.ingest(new IngestEventRequest(
                null, "swifthaul", "ship-cr-2", EventStatus.DELIVERED, later, later, "Rotterdam"));

        assertThat(delivered.outcome()).isEqualTo(IngestOutcome.ACCEPTED_STATE_CHANGED);
        assertThat(service.getShipment("ship-cr-2").currentStatus()).isEqualTo(EventStatus.DELIVERED);
        assertThat(service.getShipment("ship-cr-2").eventsProcessed()).isEqualTo(2);
    }

    @Test
    void stableEventIdPartnersAreUnaffectedByTheChange() {
        Instant occurredAt = Instant.parse("2026-03-10T09:00:00Z");

        IngestEventResponse first = service.ingest(new IngestEventRequest(
                "evt-stable-1", "dhl", "ship-cr-3", EventStatus.IN_TRANSIT, occurredAt, occurredAt, "Berlin"));
        IngestEventResponse duplicate = service.ingest(new IngestEventRequest(
                "evt-stable-1", "dhl", "ship-cr-3", EventStatus.IN_TRANSIT, occurredAt, occurredAt.plusSeconds(5), "Berlin"));

        assertThat(first.outcome()).isEqualTo(IngestOutcome.ACCEPTED_STATE_CHANGED);
        assertThat(duplicate.outcome()).isEqualTo(IngestOutcome.REJECTED_DUPLICATE);
    }
}
