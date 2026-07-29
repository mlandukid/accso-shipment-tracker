package com.accso.shipment.service;

import com.accso.shipment.dto.IngestEventRequest;
import com.accso.shipment.model.EventStatus;
import com.accso.shipment.model.IngestOutcome;
import com.accso.shipment.model.ShipmentEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentStateResolverTest {

    private static final Instant T0 = Instant.parse("2026-03-10T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-10T14:00:00Z");

    private ShipmentEvent accepted(String eventId, EventStatus status, Instant occurredAt, IngestOutcome outcome) {
        return new ShipmentEvent(eventId, "dhl::" + eventId, "dhl", "ship-1", status, occurredAt, occurredAt, "Amsterdam", outcome);
    }

    private IngestEventRequest request(String eventId, EventStatus status, Instant occurredAt) {
        return new IngestEventRequest(eventId, "dhl", "ship-1", status, occurredAt, occurredAt, "Amsterdam");
    }

    @Test
    void firstEventForShipmentAlwaysChangesState() {
        IngestOutcome outcome = ShipmentStateResolver.classify(request("evt-1", EventStatus.LABEL_CREATED, T0), Optional.empty());
        assertThat(outcome).isEqualTo(IngestOutcome.ACCEPTED_STATE_CHANGED);
    }

    @Test
    void newerEventChangesCurrentState() {
        ShipmentEvent current = accepted("evt-1", EventStatus.IN_TRANSIT, T1, IngestOutcome.ACCEPTED_STATE_CHANGED);
        IngestOutcome outcome = ShipmentStateResolver.classify(request("evt-2", EventStatus.DELIVERED, T2), Optional.of(current));
        assertThat(outcome).isEqualTo(IngestOutcome.ACCEPTED_STATE_CHANGED);
    }

    @Test
    void outOfOrderEventIsAcceptedButDoesNotChangeState() {
        ShipmentEvent current = accepted("evt-1", EventStatus.IN_TRANSIT, T2, IngestOutcome.ACCEPTED_STATE_CHANGED);
        IngestOutcome outcome = ShipmentStateResolver.classify(request("evt-2", EventStatus.HANDED_TO_CARRIER, T0), Optional.of(current));
        assertThat(outcome).isEqualTo(IngestOutcome.ACCEPTED_NO_STATE_CHANGE);
    }

    @Test
    void resolveCurrentPicksLatestOccurredAtRegardlessOfInsertOrder() {
        ShipmentEvent late = accepted("evt-2", EventStatus.HANDED_TO_CARRIER, T0, IngestOutcome.ACCEPTED_NO_STATE_CHANGE);
        ShipmentEvent early = accepted("evt-1", EventStatus.IN_TRANSIT, T2, IngestOutcome.ACCEPTED_STATE_CHANGED);
        Optional<ShipmentEvent> current = ShipmentStateResolver.resolveCurrent(List.of(late, early));
        assertThat(current).isPresent();
        assertThat(current.get().getStatus()).isEqualTo(EventStatus.IN_TRANSIT);
    }

    @Test
    void sameTimestampDifferentStatusIsAConflictResolvedByPrecedence() {
        ShipmentEvent current = accepted("evt-1", EventStatus.IN_TRANSIT, T1, IngestOutcome.ACCEPTED_STATE_CHANGED);
        IngestOutcome outcome = ShipmentStateResolver.classify(request("evt-2", EventStatus.DELIVERY_EXCEPTION, T1), Optional.of(current));
        assertThat(outcome).isEqualTo(IngestOutcome.ACCEPTED_CONFLICT);

        ShipmentEvent conflictingEvent = accepted("evt-2", EventStatus.DELIVERY_EXCEPTION, T1, IngestOutcome.ACCEPTED_CONFLICT);
        Optional<ShipmentEvent> winner = ShipmentStateResolver.resolveCurrent(List.of(current, conflictingEvent));
        assertThat(winner).isPresent();
        assertThat(winner.get().getStatus()).isEqualTo(EventStatus.DELIVERY_EXCEPTION);
    }

    @Test
    void sameTimestampSameStatusIsNotTreatedAsAConflict() {
        ShipmentEvent current = accepted("evt-1", EventStatus.IN_TRANSIT, T1, IngestOutcome.ACCEPTED_STATE_CHANGED);
        IngestOutcome outcome = ShipmentStateResolver.classify(request("evt-2", EventStatus.IN_TRANSIT, T1), Optional.of(current));
        assertThat(outcome).isEqualTo(IngestOutcome.ACCEPTED_NO_STATE_CHANGE);
    }

    @Test
    void hasConflictDetectsAnyConflictInHistory() {
        ShipmentEvent normal = accepted("evt-1", EventStatus.IN_TRANSIT, T1, IngestOutcome.ACCEPTED_STATE_CHANGED);
        ShipmentEvent conflict = accepted("evt-2", EventStatus.DELIVERY_EXCEPTION, T1, IngestOutcome.ACCEPTED_CONFLICT);
        assertThat(ShipmentStateResolver.hasConflict(List.of(normal, conflict))).isTrue();
        assertThat(ShipmentStateResolver.hasConflict(List.of(normal))).isFalse();
    }
}
