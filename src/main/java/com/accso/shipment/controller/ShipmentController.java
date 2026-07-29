package com.accso.shipment.controller;

import com.accso.shipment.dto.ShipmentHistoryView;
import com.accso.shipment.dto.ShipmentView;
import com.accso.shipment.service.ShipmentEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentEventService service;

    public ShipmentController(ShipmentEventService service) {
        this.service = service;
    }

    @GetMapping("/{shipmentId}")
    public ShipmentView getShipment(@PathVariable String shipmentId) {
        return service.getShipment(shipmentId);
    }

    @GetMapping("/{shipmentId}/events")
    public ShipmentHistoryView getHistory(@PathVariable String shipmentId) {
        return service.getHistory(shipmentId);
    }
}
