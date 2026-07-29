package com.accso.shipment.service;

import com.accso.shipment.dto.IngestEventRequest;
import com.accso.shipment.model.EventStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DedupeKeyGeneratorTest {

    private final DedupeKeyGenerator generator = new DedupeKeyGenerator();
    private static final Instant T1 = Instant.parse("2026-03-10T12:00:00Z");

    @Test
    void sameEventIdProducesSameKeyRegardlessOfOtherFields() {
        IngestEventRequest first = new IngestEventRequest("evt-1", "dhl", "ship-1", EventStatus.IN_TRANSIT, T1, T1, "Amsterdam");
        IngestEventRequest resend = new IngestEventRequest("evt-1", "dhl", "ship-1", EventStatus.IN_TRANSIT, T1, T1.plusSeconds(90), "Amsterdam");

        assertThat(generator.generate(first)).isEqualTo(generator.generate(resend));
    }

    @Test
    void differentPartnersWithSameEventIdDoNotCollide() {
        IngestEventRequest dhl = new IngestEventRequest("evt-1", "dhl", "ship-1", EventStatus.IN_TRANSIT, T1, T1, "Amsterdam");
        IngestEventRequest ups = new IngestEventRequest("evt-1", "ups", "ship-1", EventStatus.IN_TRANSIT, T1, T1, "Amsterdam");

        assertThat(generator.generate(dhl)).isNotEqualTo(generator.generate(ups));
    }

    @Test
    void differentEventIdsForSameShipmentProduceDifferentKeys() {
        IngestEventRequest a = new IngestEventRequest("evt-1", "dhl", "ship-1", EventStatus.IN_TRANSIT, T1, T1, "Amsterdam");
        IngestEventRequest b = new IngestEventRequest("evt-2", "dhl", "ship-1", EventStatus.DELIVERED, T1, T1, "Amsterdam");

        assertThat(generator.generate(a)).isNotEqualTo(generator.generate(b));
    }
}
