package com.accso.shipment.model;

/**
 * Suggested status values from the brief, given an explicit precedence rank.
 * <p>
 * The rank is ONLY used as a deterministic tie-breaker when two accepted
 * events for the same shipment share the exact same {@code occurredAt}
 * timestamp but report a different status (a genuine data conflict - see
 * ADR-002). It is a pragmatic, documented assumption, not a confirmed
 * business rule: in reality "which status wins a tie" is a product/ops
 * decision. DELIVERY_EXCEPTION is ranked ahead of the shipped-forward states
 * because an exception is operationally more urgent/attention-worthy than a
 * routine milestone at the same instant.
 */
public enum EventStatus {
    LABEL_CREATED(0),
    HANDED_TO_CARRIER(1),
    IN_TRANSIT(2),
    OUT_FOR_DELIVERY(3),
    DELIVERY_EXCEPTION(4),
    DELIVERED(5),
    RETURNED(6);

    private final int precedence;

    EventStatus(int precedence) {
        this.precedence = precedence;
    }

    public int getPrecedence() {
        return precedence;
    }
}
