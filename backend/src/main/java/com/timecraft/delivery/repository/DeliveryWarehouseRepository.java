package com.timecraft.delivery.repository;

import com.timecraft.delivery.entity.DeliveryWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryWarehouseRepository extends JpaRepository<DeliveryWarehouse, Long> {
    List<DeliveryWarehouse> findAllByOrderByDefaultWarehouseDescNameAsc();
    Optional<DeliveryWarehouse> findFirstByDefaultWarehouseTrueAndActiveTrue();
    Optional<DeliveryWarehouse> findFirstByActiveTrueOrderByIdAsc();
    Optional<DeliveryWarehouse> findByCodeIgnoreCase(String code);
}
