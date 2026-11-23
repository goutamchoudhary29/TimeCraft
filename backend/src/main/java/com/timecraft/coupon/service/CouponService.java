package com.timecraft.coupon.service;

import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.entity.User;
import com.timecraft.common.util.Money;
import com.timecraft.coupon.entity.Coupon;
import com.timecraft.coupon.entity.CouponUsage;
import com.timecraft.coupon.entity.CouponUsageStatus;
import com.timecraft.coupon.entity.DiscountType;
import com.timecraft.coupon.repository.CouponRepository;
import com.timecraft.coupon.repository.CouponUsageRepository;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.order.entity.Order;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;
    private final AuditLogService auditLogService;

    public record CouponRequest(
            @NotBlank @Size(max = 40) String code,
            @Size(max = 255) String description,
            @NotNull DiscountType discountType,
            @NotNull @DecimalMin("0.01") BigDecimal discountValue,
            @DecimalMin("0.00") BigDecimal minOrderValue,
            BigDecimal maxDiscount,
            @NotNull Instant startsAt,
            @NotNull Instant expiresAt,
            Integer usageLimit,
            Integer perCustomerLimit,
            boolean active
    ) {
    }

    public record CouponDto(
            Long id,
            String code,
            String description,
            DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderValue,
            BigDecimal maxDiscount,
            Instant startsAt,
            Instant expiresAt,
            Integer usageLimit,
            Integer perCustomerLimit,
            int usedCount,
            boolean active
    ) {
        public static CouponDto from(Coupon c) {
            return new CouponDto(c.getId(), c.getCode(), c.getDescription(), c.getDiscountType(),
                    c.getDiscountValue(), c.getMinOrderValue(), c.getMaxDiscount(),
                    c.getStartsAt(), c.getExpiresAt(), c.getUsageLimit(),
                    c.getPerCustomerLimit(), c.getUsedCount(), c.isActive());
        }
    }

    public record CouponEvaluation(Coupon coupon, BigDecimal discountAmount) {
    }

    @Transactional(readOnly = true)
    public CouponEvaluation evaluate(String rawCode, BigDecimal subtotal, Long userId) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(rawCode.trim())
                .orElseThrow(() -> BusinessException.badRequest("INVALID_COUPON", "Invalid coupon code"));
        return evaluateEntity(coupon, subtotal, userId);
    }

    @Transactional
    public CouponEvaluation evaluateForOrderLocking(String rawCode, BigDecimal subtotal, Long userId) {
        Coupon coupon = couponRepository.findByCodeForUpdate(rawCode.trim())
                .orElseThrow(() -> BusinessException.badRequest("INVALID_COUPON", "Invalid coupon code"));
        return evaluateEntity(coupon, subtotal, userId);
    }

    private CouponEvaluation evaluateEntity(Coupon coupon, BigDecimal subtotal, Long userId) {
        Instant now = Instant.now();
        if (!coupon.isActive()) {
            throw BusinessException.badRequest("COUPON_INACTIVE", "This coupon is not active");
        }
        if (now.isBefore(coupon.getStartsAt())) {
            throw BusinessException.badRequest("COUPON_NOT_STARTED", "This coupon is not active yet");
        }
        if (now.isAfter(coupon.getExpiresAt())) {
            throw BusinessException.badRequest("COUPON_EXPIRED", "This coupon has expired");
        }
        if (coupon.getMinOrderValue() != null && subtotal.compareTo(coupon.getMinOrderValue()) < 0) {
            throw BusinessException.badRequest("COUPON_MIN_ORDER_NOT_MET",
                    "Minimum order value of ₹" + coupon.getMinOrderValue() + " is required for coupon " + coupon.getCode());
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw BusinessException.badRequest("COUPON_USAGE_LIMIT_REACHED",
                    "This coupon has reached its maximum usage limit");
        }
        if (userId != null && coupon.getPerCustomerLimit() != null) {
            long customerUsed = usageRepository.countByCouponIdAndUserIdAndStatusIn(
                    coupon.getId(), userId, List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.REDEEMED));
            if (customerUsed >= coupon.getPerCustomerLimit()) {
                throw BusinessException.badRequest("COUPON_CUSTOMER_LIMIT_REACHED",
                        "You have already used coupon " + coupon.getCode() + " the maximum allowed times");
            }
        }

        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = Money.percentOf(subtotal, coupon.getDiscountValue());
            if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
                discount = Money.of(coupon.getMaxDiscount());
            }
        } else {
            discount = Money.of(coupon.getDiscountValue());
        }
        discount = Money.min(discount, subtotal);
        return new CouponEvaluation(coupon, discount);
    }

    @Transactional
    public void reserveForOrder(Coupon coupon, User user, Order order, BigDecimal discountAmount) {
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        couponRepository.save(coupon);

        CouponUsage usage = CouponUsage.builder()
                .coupon(coupon)
                .user(user)
                .order(order)
                .discountAmount(discountAmount)
                .status(CouponUsageStatus.RESERVED)
                .build();
        usageRepository.save(usage);
    }

    @Transactional
    public void markRedeemedForOrder(Long orderId) {
        usageRepository.findByOrderId(orderId).ifPresent(u -> {
            u.setStatus(CouponUsageStatus.REDEEMED);
            usageRepository.save(u);
        });
    }

    @Transactional
    public void releaseForOrder(Long orderId) {
        usageRepository.findByOrderId(orderId).ifPresent(u -> {
            if (u.getStatus() != CouponUsageStatus.RELEASED) {
                u.setStatus(CouponUsageStatus.RELEASED);
                usageRepository.save(u);
                Coupon c = couponRepository.findByCodeForUpdate(u.getCoupon().getCode()).orElse(u.getCoupon());
                c.setUsedCount(Math.max(0, c.getUsedCount() - 1));
                couponRepository.save(c);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<CouponDto> listAllAdmin() {
        return couponRepository.findAll().stream().map(CouponDto::from).toList();
    }

    @Transactional
    public CouponDto create(CouponRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw BusinessException.conflict("DUPLICATE_COUPON_CODE", "Coupon code " + code + " already exists");
        }
        validateCouponRequest(req);
        Coupon coupon = new Coupon();
        apply(coupon, req, code);
        coupon = couponRepository.save(coupon);
        auditLogService.logCurrentActor("COUPON_CREATED", "COUPON", coupon.getId(),
                "Created coupon " + coupon.getCode());
        return CouponDto.from(coupon);
    }

    @Transactional
    public CouponDto update(Long id, CouponRequest req) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id));
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (!coupon.getCode().equalsIgnoreCase(code) && couponRepository.existsByCodeIgnoreCase(code)) {
            throw BusinessException.conflict("DUPLICATE_COUPON_CODE", "Coupon code " + code + " already exists");
        }
        validateCouponRequest(req);
        apply(coupon, req, code);
        coupon = couponRepository.save(coupon);
        auditLogService.logCurrentActor("COUPON_UPDATED", "COUPON", coupon.getId(),
                "Updated coupon " + coupon.getCode() + " active=" + coupon.isActive());
        return CouponDto.from(coupon);
    }

    @Transactional
    public CouponDto setActive(Long id, boolean active) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", id));
        coupon.setActive(active);
        coupon = couponRepository.save(coupon);
        auditLogService.logCurrentActor("COUPON_STATUS_CHANGED", "COUPON", id,
                "Coupon " + coupon.getCode() + " active=" + active);
        return CouponDto.from(coupon);
    }

    private static void validateCouponRequest(CouponRequest req) {
        if (!req.expiresAt().isAfter(req.startsAt())) {
            throw BusinessException.badRequest("INVALID_COUPON_DATES", "Expiry date must be after start date");
        }
        if (req.discountType() == DiscountType.PERCENTAGE
                && req.discountValue().compareTo(new BigDecimal("90")) > 0) {
            throw BusinessException.badRequest("INVALID_DISCOUNT_PERCENT",
                    "Percentage discount cannot exceed 90%");
        }
    }

    private static void apply(Coupon target, CouponRequest req, String code) {
        target.setCode(code);
        target.setDescription(req.description());
        target.setDiscountType(req.discountType());
        target.setDiscountValue(Money.of(req.discountValue()));
        target.setMinOrderValue(req.minOrderValue() != null ? Money.of(req.minOrderValue()) : Money.ZERO);
        target.setMaxDiscount(req.maxDiscount() != null ? Money.of(req.maxDiscount()) : null);
        target.setStartsAt(req.startsAt());
        target.setExpiresAt(req.expiresAt());
        target.setUsageLimit(req.usageLimit());
        target.setPerCustomerLimit(req.perCustomerLimit());
        target.setActive(req.active());
    }
}
