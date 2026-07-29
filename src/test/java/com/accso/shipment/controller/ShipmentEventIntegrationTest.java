package com.accso.shipment.controller;

import com.accso.shipment.dto.IngestEventRequest;
import com.accso.shipment.dto.IngestEventResponse;
import com.accso.shipment.dto.ShipmentHistoryView;
import com.accso.shipment.dto.ShipmentView;
import com.accso.shipment.model.EventStatus;
import com.accso.shipment.model.IngestOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShipmentEventIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void fullFlow_ingestThenQueryCurrentStateAndHistory() {
        Instant t1 = Instant.parse("2026-03-10T12:00:00Z");
        Instant t2 = Instant.parse("2026-03-10T14:00:00Z");

        post(new IngestEventRequest("evt-1", "dhl", "ship-int-1", EventStatus.LABEL_CREATED, t1, t1, "Berlin"));
        ResponseEntity<IngestEventResponse> second = post(
                new IngestEventRequest("evt-2", "dhl", "ship-int-1", EventStatus.IN_TRANSIT, t2, t2, "Amsterdam"));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().outcome()).isEqualTo(IngestOutcome.ACCEPTED_STATE_CHANGED);

        ResponseEntity<ShipmentView> current = rest.getForEntity(url("/shipments/ship-int-1"), ShipmentView.class);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(current.getBody().currentStatus()).isEqualTo(EventStatus.IN_TRANSIT);
        assertThat(current.getBody().eventsProcessed()).isEqualTo(2);

        ResponseEntity<ShipmentHistoryView> history = rest.getForEntity(url("/shipments/ship-int-1/events"), ShipmentHistoryView.class);
        assertThat(history.getBody().events()).hasSize(2);
        assertThat(history.getBody().events().get(0).eventId()).isEqualTo("evt-2");
    }

    @Test
    void duplicateEvent_isRejectedAndDoesNotAffectEventCount() {
        Instant t1 = Instant.parse("2026-03-10T12:00:00Z");
        IngestEventRequest request = new IngestEventRequest("evt-dup", "dhl", "ship-int-2", EventStatus.IN_TRANSIT, t1, t1, "Amsterdam");

        ResponseEntity<IngestEventResponse> first = post(request);
        ResponseEntity<IngestEventResponse> duplicate = post(request);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duplicate.getBody().outcome()).isEqualTo(IngestOutcome.REJECTED_DUPLICATE);

        ResponseEntity<ShipmentView> current = rest.getForEntity(url("/shipments/ship-int-2"), ShipmentView.class);
        assertThat(current.getBody().eventsProcessed()).isEqualTo(1);
    }

    @Test
    void unknownShipment_returns404() {
        ResponseEntity<String> response = rest.getForEntity(url("/shipments/does-not-exist"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void missingRequiredField_returns400() {
        IngestEventRequest invalid = new IngestEventRequest("evt-x", "", "ship-int-3", EventStatus.IN_TRANSIT, Instant.now(), Instant.now(), "x");
        ResponseEntity<String> response = rest.postForEntity(url("/shipment-events"), invalid, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<IngestEventResponse> post(IngestEventRequest request) {
        return rest.postForEntity(url("/shipment-events"), request, IngestEventResponse.class);
    }
}
