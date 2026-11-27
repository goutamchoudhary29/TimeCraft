package com.timecraft.delivery.controller;

import com.timecraft.cart.service.CartService;
import com.timecraft.delivery.dto.DeliveryDtos.CartDeliverySummaryDto;
import com.timecraft.delivery.dto.DeliveryDtos.CartItemDeliveryDto;
import com.timecraft.delivery.dto.DeliveryDtos.DeliveryEstimateDto;
import com.timecraft.delivery.service.DeliveryEstimationService;
import com.timecraft.security.SecurityUtils;
import com.timecraft.security.TimeCraftPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private static final String GUEST_HDR = "X-Guest-Token";

    private final DeliveryEstimationService deliveryService;
    private final CartService cartService;

    @GetMapping("/estimate")
    public DeliveryEstimateDto estimateProductDelivery(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) String pinCode
    ) {
        return deliveryService.estimateByProductLookup(productId, slug, variantId, pinCode);
    }

    @GetMapping("/cart-estimates")
    public CartDeliverySummaryDto estimateCartDelivery(
            @RequestHeader(value = GUEST_HDR, required = false) String guestToken,
            @RequestParam(required = false) String pinCode
    ) {
        Long uid = SecurityUtils.currentPrincipal().map(TimeCraftPrincipal::getId).orElse(null);
        var cart = cartService.getCart(uid, guestToken);
        List<CartItemDeliveryDto> itemEstimates = new ArrayList<>();
        Set<String> warehouses = new LinkedHashSet<>();

        for (var item : cart.items()) {
            DeliveryEstimateDto est = deliveryService.estimateByProductLookup(
                    item.productId(), item.productSlug(), item.variantId(), pinCode);
            if (est.warehouseName() != null && !est.warehouseName().isBlank()) {
                warehouses.add(est.warehouseName() + (est.warehouseCity() != null ? " (" + est.warehouseCity() + ")" : ""));
            }
            itemEstimates.add(new CartItemDeliveryDto(
                    item.id(),
                    item.productId(),
                    item.productName(),
                    item.brandName(),
                    est
            ));
        }

        boolean split = warehouses.size() > 1;
        String summaryNote = split
                ? "Items in your bag ship from " + warehouses.size() + " different vaults (" + String.join(" & ", warehouses) + ") and may arrive in separate insured parcels."
                : (warehouses.isEmpty() ? "" : "Dispatches together from " + warehouses.iterator().next() + ".");

        return new CartDeliverySummaryDto(
                pinCode != null && !pinCode.isBlank() ? pinCode.trim() : null,
                split,
                warehouses.size(),
                new ArrayList<>(warehouses),
                summaryNote,
                itemEstimates
        );
    }
}
