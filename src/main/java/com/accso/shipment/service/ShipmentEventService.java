package com.accso.shipment.service;

import com.accso.shipment.dto.*;
import com.accso.shipment.exception.ShipmentNotFoundException;
import com.accso.shipment.model.IngestOutcome;
import com.accso.shipment.model.ShipmentEvent;
import com.accso.shipment.repository.ShipmentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ShipmentEventService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventService.class);

    private final ShipmentEventRepository repository;
    private final DedupeKeyGenerator dedupeKeyGenerator;

    public ShipmentEventService(ShipmentEventRepository repository, DedupeKeyGenerator dedupeKeyGenerator) {
        this.repository = repository;
        this.dedupeKeyGenerator = dedupeKeyGenerator;
    }

    @Transactional
    public IngestEventResponse ingest(IngestEventRequest rawRequest) {
        // If the courier didn't send receivedAt, stamp it with server time rather than rejecting the event.
        IngestEventRequest request = rawRequest.receivedAt() != null
                ? rawRequest
                : new IngestEventRequest(rawRequest.eventId(), rawRequest.partner(), rawRequest.shipmentId(),
                        rawRequest.status(), rawRequest.occurredAt(), Instant.now(), rawRequest.location());

        String dedupeKey = dedupeKeyGenerator.generate(request);

        if (repository.existsByDedupeKey(dedupeKey)) {
            log.info("event_rejected_duplicate partner={} shipmentId={} eventId={}",
                    request.partner(), request.shipmentId(), request.eventId());
            return IngestEventResponse.of(IngestOutcome.REJECTED_DUPLICATE, request);
        }

        List<ShipmentEvent> existing = repository.findByShipmentIdOrderByOccurredAtDesc(request.shipmentId());
        Optional<ShipmentEvent> currentMax = ShipmentStateResolver.resolveCurrent(existing);
        IngestOutcome outcome = ShipmentStateResolver.classify(request, currentMax);

        ShipmentEvent entity = new ShipmentEvent(
                request.eventId(), dedupeKey, request.partner(), request.shipmentId(),
                request.status(), request.occurredAt(), request.receivedAt(), request.location(), outcome
        );

        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException raceLostToConcurrentDuplicate) {
            log.info("event_rejected_duplicate_race partner={} shipmentId={} eventId={}",
                    request.partner(), request.shipmentId(), request.eventId());
            return IngestEventResponse.of(IngestOutcome.REJECTED_DUPLICATE, request);
        }

        log.info("event_accepted outcome={} partner={} shipmentId={} eventId={} status={} occurredAt={}",
                outcome, request.partner(), request.shipmentId(), request.eventId(),
                request.status(), request.occurredAt());

        return IngestEventResponse.of(outcome, request);
    }

    @Transactional(readOnly = true)
    public ShipmentView getShipment(String shipmentId) {
        List<ShipmentEvent> events = repository.findByShipmentIdOrderByOccurredAtDesc(shipmentId);
        if (events.isEmpty()) {
            throw new ShipmentNotFoundException(shipmentId);
        }
        ShipmentEvent current = ShipmentStateResolver.resolveCurrent(events).orElseThrow();
        boolean conflict = ShipmentStateResolver.hasConflict(events);

        String explanation = "Reflects the accepted event with the latest occurredAt ("
                + current.getOccurredAt() + ") out of " + events.size() + " accepted event(s) for this shipment."
                + (conflict
                    ? " Note: a conflicting update was received for the same occurredAt timestamp; ties are"
                        + " resolved by status precedence (see ADR docs) - check the event history for details."
                    : "");

        return new ShipmentView(shipmentId, current.getStatus(), current.getOccurredAt(), events.size(), explanation);
    }

    @Transactional(readOnly = true)
    public ShipmentHistoryView getHistory(String shipmentId) {
        List<ShipmentEvent> events = repository.findByShipmentIdOrderByOccurredAtDesc(shipmentId);
        if (events.isEmpty()) {
            throw new ShipmentNotFoundException(shipmentId);
        }
        List<ShipmentEventView> views = events.stream().map(ShipmentEventView::from).toList();
        return new ShipmentHistoryView(shipmentId, "occurredAt DESC (business time, not arrival time)", views);
    }
}
