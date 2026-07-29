package com.accso.shipment.model;

/**
 * What happened when an inbound event was processed. Every accepted event
 * keeps this on its own row, so the history endpoint can show exactly how
 * each event was handled without recomputing anything.
 */
public enum IngestOutcome {
    ACCEPTED_STATE_CHANGED,
    ACCEPTED_NO_STATE_CHANGE,
    ACCEPTED_CONFLICT,
    REJECTED_DUPLICATE
}
