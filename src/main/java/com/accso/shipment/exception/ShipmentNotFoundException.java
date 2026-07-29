package com.accso.shipment.exception;

public class ShipmentNotFoundException extends RuntimeException {
    public ShipmentNotFoundException(String shipmentId) {
        super("No events found for shipmentId '" + shipmentId + "'");
    }
}
