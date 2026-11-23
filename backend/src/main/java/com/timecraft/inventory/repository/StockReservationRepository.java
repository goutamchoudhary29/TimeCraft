package com.timecraft.inventory.repository;

import com.timecraft.inventory.entity.StockReservation;
import com.timecraft.inventory.entity.StockReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderId(Long orderId);

    List<StockReservation> findByOrderIdAndStatus(Long orderId, StockReservationStatus status);

    List<StockReservation> findByStatusAndExpiresAtBefore(StockReservationStatus status, Instant cutoff);
}
