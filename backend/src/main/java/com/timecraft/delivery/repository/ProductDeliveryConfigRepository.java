package com.timecraft.delivery.repository;

import com.timecraft.delivery.entity.ProductDeliveryConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductDeliveryConfigRepository extends JpaRepository<ProductDeliveryConfig, Long> {
    List<ProductDeliveryConfig> findByProductIdIn(Collection<Long> productIds);
}
