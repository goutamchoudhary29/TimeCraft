package com.timecraft.cart.service;

import com.timecraft.admin.service.StoreConfigService;
import com.timecraft.common.util.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Configurable tax (GST) and shipping breakdown calculator.
 * Tax rate, tax inclusivity and free-shipping thresholds are read from store_settings.
 */
@Service
@RequiredArgsConstructor
public class PriceBreakdownCalculator {

    private final StoreConfigService storeConfigService;

    public record TotalsBreakdown(
            String currency,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal discountedSubtotal,
            String taxLabel,
            BigDecimal taxRatePercent,
            boolean taxInclusive,
            BigDecimal taxTotal,
            BigDecimal shippingTotal,
            BigDecimal freeShippingThreshold,
            BigDecimal grandTotal
    ) {
    }

    public TotalsBreakdown calculate(BigDecimal rawSubtotal, BigDecimal rawDiscount) {
        String currency = storeConfigService.getString("store.currency", "INR");
        String taxLabel = storeConfigService.getString("tax.label", "GST");
        BigDecimal taxRate = storeConfigService.getDecimal("tax.rate_percent", "18.00");
        boolean taxInclusive = storeConfigService.getBoolean("tax.inclusive", true);
        BigDecimal flatShipping = storeConfigService.getDecimal("shipping.flat_rate", "250.00");
        BigDecimal freeThreshold = storeConfigService.getDecimal("shipping.free_threshold", "10000.00");

        BigDecimal subtotal = Money.of(rawSubtotal);
        BigDecimal discount = Money.min(Money.of(rawDiscount), subtotal);
        BigDecimal discounted = Money.max(Money.ZERO, subtotal.subtract(discount));

        if (subtotal.signum() == 0) {
            return new TotalsBreakdown(currency, Money.ZERO, Money.ZERO, Money.ZERO,
                    taxLabel, taxRate, taxInclusive, Money.ZERO, Money.ZERO, freeThreshold, Money.ZERO);
        }

        BigDecimal taxTotal;
        if (taxInclusive) {
            // Selling price already includes GST: extract embedded tax = discounted * rate / (100 + rate)
            BigDecimal divisor = Money.HUNDRED.add(taxRate);
            taxTotal = discounted.multiply(taxRate).divide(divisor, 2, RoundingMode.HALF_UP);
        } else {
            taxTotal = Money.percentOf(discounted, taxRate);
        }

        BigDecimal shipping = discounted.compareTo(freeThreshold) >= 0 ? Money.ZERO : flatShipping;
        BigDecimal grandTotal = taxInclusive
                ? discounted.add(shipping)
                : discounted.add(taxTotal).add(shipping);

        return new TotalsBreakdown(
                currency,
                subtotal,
                discount,
                discounted,
                taxLabel,
                taxRate,
                taxInclusive,
                Money.of(taxTotal),
                Money.of(shipping),
                freeThreshold,
                Money.of(grandTotal)
        );
    }
}
