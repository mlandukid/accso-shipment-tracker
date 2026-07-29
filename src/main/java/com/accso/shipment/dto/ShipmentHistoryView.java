package com.accso.shipment.dto;

import java.util.List;

public record ShipmentHistoryView(
        String shipmentId,
        String orderedBy,
        List<ShipmentEventView> events
) {
}
