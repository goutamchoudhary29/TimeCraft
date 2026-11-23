package com.timecraft.shipment.repository;

import com.timecraft.shipment.entity.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderId(Long orderId);

    Optional<Shipment> findByTrackingNumberIgnoreCase(String trackingNumber);

    Page<Shipment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
