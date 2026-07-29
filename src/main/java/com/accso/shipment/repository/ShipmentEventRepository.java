package com.accso.shipment.repository;

import com.accso.shipment.model.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    List<ShipmentEvent> findByShipmentIdOrderByOccurredAtDesc(String shipmentId);
}
