package com.accso.shipment.controller;

import com.accso.shipment.dto.IngestEventRequest;
import com.accso.shipment.dto.IngestEventResponse;
import com.accso.shipment.model.IngestOutcome;
import com.accso.shipment.service.ShipmentEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shipment-events")
public class ShipmentEventController {

    private final ShipmentEventService service;

    public ShipmentEventController(ShipmentEventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<IngestEventResponse> ingest(@Valid @RequestBody IngestEventRequest request) {
        IngestEventResponse response = service.ingest(request);
        HttpStatus status = response.outcome() == IngestOutcome.REJECTED_DUPLICATE
                ? HttpStatus.OK
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}
