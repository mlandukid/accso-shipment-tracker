package com.accso.shipment.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One row per ACCEPTED inbound event. This table is both:
 *   1) the audit trail required by the brief (append-only, nothing is ever
 *      mutated or deleted), and
 *   2) the source of truth current state is derived from - see
 *      ShipmentStateResolver and ADR-001.
 *
 * Duplicates are never inserted here at all (see dedupeKey + the unique
 * constraint below and ADR-002) - that's what makes "audit trail of accepted
 * events" and "detect duplicates" the same mechanism instead of two.
 */
@Entity
@Table(
        name = "shipment_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_dedupe_key", columnNames = "dedupe_key"),
        indexes = @Index(name = "idx_shipment_id", columnList = "shipment_id")
)
public class ShipmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    @Column(nullable = false)
    private String partner;

    @Column(name = "shipment_id", nullable = false)
    private String shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestOutcome outcome;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ShipmentEvent() {
        // JPA
    }

    public ShipmentEvent(String eventId, String dedupeKey, String partner, String shipmentId,
                          EventStatus status, Instant occurredAt, Instant receivedAt,
                          String location, IngestOutcome outcome) {
        this.eventId = eventId;
        this.dedupeKey = dedupeKey;
        this.partner = partner;
        this.shipmentId = shipmentId;
        this.status = status;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.location = location;
        this.outcome = outcome;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getDedupeKey() { return dedupeKey; }
    public String getPartner() { return partner; }
    public String getShipmentId() { return shipmentId; }
    public EventStatus getStatus() { return status; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getLocation() { return location; }
    public IngestOutcome getOutcome() { return outcome; }
    public Instant getCreatedAt() { return createdAt; }
}
