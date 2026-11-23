package com.timecraft.coupon.repository;

import com.timecraft.coupon.entity.CouponUsage;
import com.timecraft.coupon.entity.CouponUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    Optional<CouponUsage> findByOrderId(Long orderId);

    long countByCouponIdAndUserIdAndStatusIn(Long couponId, Long userId, Collection<CouponUsageStatus> statuses);
}
