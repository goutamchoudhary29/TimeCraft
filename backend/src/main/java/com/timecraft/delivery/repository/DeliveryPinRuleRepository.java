package com.timecraft.delivery.repository;

import com.timecraft.delivery.entity.DeliveryPinRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryPinRuleRepository extends JpaRepository<DeliveryPinRule, Long> {
    List<DeliveryPinRule> findAllByOrderByPinPatternAsc();
    Optional<DeliveryPinRule> findByPinPatternIgnoreCase(String pinPattern);
}
