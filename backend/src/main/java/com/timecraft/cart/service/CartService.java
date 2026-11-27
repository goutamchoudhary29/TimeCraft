package com.timecraft.cart.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.timecraft.admin.service.StoreConfigService;
import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.cart.entity.Cart;
import com.timecraft.cart.entity.CartItem;
import com.timecraft.cart.repository.CartItemRepository;
import com.timecraft.cart.repository.CartRepository;
import com.timecraft.cart.service.PriceBreakdownCalculator.TotalsBreakdown;
import com.timecraft.common.util.Money;
import com.timecraft.common.util.TokenUtils;
import com.timecraft.coupon.service.CouponService;
import com.timecraft.delivery.dto.DeliveryDtos.DeliveryEstimateDto;
import com.timecraft.delivery.service.DeliveryEstimationService;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.inventory.service.InventoryService;
import com.timecraft.product.entity.Product;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.entity.ProductVariant;
import com.timecraft.product.repository.ProductVariantRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unified server-side shopping cart supporting both guests (via opaque guest token hashed in DB)
 * and authenticated customers, with automatic guest-to-customer merge on login/register.
 * Prices are always read from the database and never from the client request.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final DeliveryEstimationService deliveryEstimationService;
    private final CouponService couponService;
    private final PriceBreakdownCalculator calculator;
    private final StoreConfigService storeConfigService;

    public record AddCartItemRequest(
            @NotNull Long variantId,
            @Min(1) int quantity
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public AddCartItemRequest(
                @JsonProperty("variantId") Long variantId,
                @JsonProperty("quantity") int quantity
        ) {
            this.variantId = variantId;
            this.quantity = quantity;
        }
    }

    public record UpdateCartItemRequest(@Min(1) int quantity) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public UpdateCartItemRequest(@JsonProperty("quantity") int quantity) {
            this.quantity = quantity;
        }
    }

    public record ApplyCouponRequest(@NotBlank String code) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public ApplyCouponRequest(@JsonProperty("code") String code) {
            this.code = code;
        }
    }

    public record CartItemDto(
            Long id,
            Long productId,
            String productName,
            String productSlug,
            String brandName,
            String modelName,
            String movementType,
            BigDecimal caseDiameterMm,
            Long variantId,
            String variantSku,
            String variantName,
            String imageUrl,
            BigDecimal regularUnitPrice,
            BigDecimal sellingUnitPrice,
            int quantity,
            int availableStock,
            int maxOrderQuantity,
            boolean available,
            BigDecimal lineSubtotal,
            DeliveryEstimateDto deliveryEstimate
    ) {
    }

    public record CartDto(
            Long cartId,
            String guestToken,
            List<CartItemDto> items,
            int totalQuantity,
            String appliedCouponCode,
            String couponMessage,
            TotalsBreakdown totals
    ) {
    }

    @Transactional
    public CartDto getCart(Long userId, String rawGuestToken) {
        CartHolder holder = resolveCart(userId, rawGuestToken, false);
        if (holder.cart() == null) {
            return emptyCartDto(holder.rawGuestToken());
        }
        return toCartDto(holder.cart(), holder.rawGuestToken(), userId);
    }

    @Transactional
    public CartDto addItem(Long userId, String rawGuestToken, AddCartItemRequest req) {
        int maxPerItem = storeConfigService.getInt("checkout.max_quantity_per_item", 5);
        if (req.quantity() <= 0 || req.quantity() > maxPerItem) {
            throw BusinessException.badRequest("INVALID_QUANTITY",
                    "Quantity must be between 1 and " + maxPerItem);
        }

        ProductVariant variant = requirePurchasableVariant(req.variantId());
        int available = inventoryService.getAvailableForVariant(variant.getId());
        if (available <= 0) {
            throw BusinessException.conflict("OUT_OF_STOCK",
                    variant.getProduct().getName() + " (" + variant.getName() + ") is currently out of stock");
        }

        CartHolder holder = resolveCart(userId, rawGuestToken, true);
        Cart cart = holder.cart();

        CartItem existing = cart.getItems().stream()
                .filter(i -> i.getVariant().getId().equals(variant.getId()))
                .findFirst()
                .orElse(null);

        int newQty = (existing != null ? existing.getQuantity() : 0) + req.quantity();
        if (newQty > maxPerItem) {
            throw BusinessException.badRequest("MAX_QUANTITY_EXCEEDED",
                    "Maximum " + maxPerItem + " units of a single timepiece allowed per order");
        }
        if (newQty > available) {
            throw BusinessException.conflict("INSUFFICIENT_STOCK",
                    "Only " + available + " unit(s) available in stock");
        }

        if (existing != null) {
            existing.setQuantity(newQty);
            cartItemRepository.save(existing);
        } else {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(newQty)
                    .build();
            cart.getItems().add(cartItemRepository.save(item));
        }

        return toCartDto(cart, holder.rawGuestToken(), userId);
    }

    @Transactional
    public CartDto updateItemQuantity(Long userId, String rawGuestToken, Long itemId, UpdateCartItemRequest req) {
        int maxPerItem = storeConfigService.getInt("checkout.max_quantity_per_item", 5);
        if (req.quantity() <= 0 || req.quantity() > maxPerItem) {
            throw BusinessException.badRequest("INVALID_QUANTITY",
                    "Quantity must be between 1 and " + maxPerItem);
        }
        CartHolder holder = resolveCart(userId, rawGuestToken, false);
        if (holder.cart() == null) {
            throw new ResourceNotFoundException("CartItem", itemId);
        }
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, holder.cart().getId())
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", itemId));

        ProductVariant variant = requirePurchasableVariant(item.getVariant().getId());
        int available = inventoryService.getAvailableForVariant(variant.getId());
        if (req.quantity() > available) {
            throw BusinessException.conflict("INSUFFICIENT_STOCK",
                    "Only " + available + " unit(s) available in stock");
        }
        item.setQuantity(req.quantity());
        cartItemRepository.save(item);
        return toCartDto(holder.cart(), holder.rawGuestToken(), userId);
    }

    @Transactional
    public CartDto removeItem(Long userId, String rawGuestToken, Long itemId) {
        CartHolder holder = resolveCart(userId, rawGuestToken, false);
        if (holder.cart() == null) {
            return emptyCartDto(holder.rawGuestToken());
        }
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, holder.cart().getId())
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", itemId));
        holder.cart().getItems().remove(item);
        cartItemRepository.delete(item);
        return toCartDto(holder.cart(), holder.rawGuestToken(), userId);
    }

    @Transactional
    public CartDto applyCoupon(Long userId, String rawGuestToken, String code) {
        CartHolder holder = resolveCart(userId, rawGuestToken, false);
        if (holder.cart() == null || holder.cart().getItems().isEmpty()) {
            throw BusinessException.badRequest("EMPTY_CART", "Add a watch to your cart before applying a coupon");
        }
        BigDecimal subtotal = computeSubtotal(holder.cart());
        var evaluation = couponService.evaluate(code, subtotal, userId);
        holder.cart().setCoupon(evaluation.coupon());
        cartRepository.save(holder.cart());
        return toCartDto(holder.cart(), holder.rawGuestToken(), userId);
    }

    @Transactional
    public CartDto removeCoupon(Long userId, String rawGuestToken) {
        CartHolder holder = resolveCart(userId, rawGuestToken, false);
        if (holder.cart() == null) {
            return emptyCartDto(holder.rawGuestToken());
        }
        holder.cart().setCoupon(null);
        cartRepository.save(holder.cart());
        return toCartDto(holder.cart(), holder.rawGuestToken(), userId);
    }

    @Transactional
    public void mergeGuestCartIntoUserCart(String rawGuestToken, Long userId) {
        if (rawGuestToken == null || rawGuestToken.isBlank() || userId == null) {
            return;
        }
        String guestHash = TokenUtils.sha256(rawGuestToken.trim());
        Cart guestCart = cartRepository.findByGuestToken(guestHash).orElse(null);
        if (guestCart == null) {
            return;
        }
        if (guestCart.getItems().isEmpty()) {
            cartRepository.delete(guestCart);
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        Cart userCart = cartRepository.findByUserId(userId).orElse(null);
        if (userCart == null) {
            guestCart.setUser(user);
            guestCart.setGuestToken(null);
            cartRepository.save(guestCart);
            return;
        }

        int maxPerItem = storeConfigService.getInt("checkout.max_quantity_per_item", 5);
        for (CartItem guestItem : new ArrayList<>(guestCart.getItems())) {
            ProductVariant variant = guestItem.getVariant();
            if (!isPurchasable(variant)) {
                continue;
            }
            int available = inventoryService.getAvailableForVariant(variant.getId());
            if (available <= 0) {
                continue;
            }
            CartItem existing = userCart.getItems().stream()
                    .filter(i -> i.getVariant().getId().equals(variant.getId()))
                    .findFirst()
                    .orElse(null);
            int mergedQty = Math.min(maxPerItem,
                    Math.min(available, (existing != null ? existing.getQuantity() : 0) + guestItem.getQuantity()));
            if (mergedQty <= 0) {
                continue;
            }
            if (existing != null) {
                existing.setQuantity(mergedQty);
            } else {
                userCart.getItems().add(CartItem.builder()
                        .cart(userCart)
                        .variant(variant)
                        .quantity(mergedQty)
                        .build());
            }
        }
        if (userCart.getCoupon() == null && guestCart.getCoupon() != null) {
            userCart.setCoupon(guestCart.getCoupon());
        }
        cartRepository.save(userCart);
        cartRepository.delete(guestCart);
    }

    @Transactional
    public void clearUserCart(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cart.setCoupon(null);
            cartRepository.save(cart);
        });
    }

    @Transactional(readOnly = true)
    public Cart requireNonEmptyUserCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> BusinessException.badRequest("EMPTY_CART", "Your shopping cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw BusinessException.badRequest("EMPTY_CART", "Your shopping cart is empty");
        }
        return cart;
    }

    private ProductVariant requirePurchasableVariant(Long variantId) {
        ProductVariant variant = variantRepository.findByIdAndDeletedFalse(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
        if (!isPurchasable(variant)) {
            throw BusinessException.badRequest("PRODUCT_UNAVAILABLE",
                    "This watch variant is currently unavailable for purchase");
        }
        return variant;
    }

    private static boolean isPurchasable(ProductVariant v) {
        if (v == null || v.isDeleted() || !v.isActive()) {
            return false;
        }
        Product p = v.getProduct();
        return p != null && !p.isDeleted() && p.getStatus() == ProductStatus.ACTIVE
                && p.getBrand().isActive() && !p.getBrand().isDeleted()
                && p.getCategory().isActive() && !p.getCategory().isDeleted();
    }

    private BigDecimal computeSubtotal(Cart cart) {
        BigDecimal subtotal = Money.ZERO;
        for (CartItem item : cart.getItems()) {
            if (isPurchasable(item.getVariant())) {
                subtotal = subtotal.add(Money.of(item.getVariant().effectiveSellingPrice())
                        .multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return Money.of(subtotal);
    }

    private CartDto toCartDto(Cart cart, String rawGuestToken, Long userId) {
        int maxPerItem = storeConfigService.getInt("checkout.max_quantity_per_item", 5);
        List<Long> variantIds = cart.getItems().stream().map(i -> i.getVariant().getId()).toList();
        Map<Long, Integer> stockMap = inventoryService.getAvailableByVariantIds(variantIds);

        List<CartItemDto> itemDtos = new ArrayList<>();
        BigDecimal subtotal = Money.ZERO;
        int totalQty = 0;

        for (CartItem item : cart.getItems()) {
            ProductVariant v = item.getVariant();
            Product p = v.getProduct();
            int availableStock = stockMap.getOrDefault(v.getId(), 0);
            int maxOrderQuantity = Math.min(maxPerItem, Math.max(0, availableStock));
            boolean available = isPurchasable(v) && availableStock >= item.getQuantity();
            BigDecimal unitSelling = Money.of(v.effectiveSellingPrice());
            BigDecimal unitRegular = Money.of(v.effectiveRegularPrice());
            BigDecimal lineSubtotal = available
                    ? Money.of(unitSelling.multiply(BigDecimal.valueOf(item.getQuantity())))
                    : Money.ZERO;
            if (available) {
                subtotal = subtotal.add(lineSubtotal);
                totalQty += item.getQuantity();
            }

            itemDtos.add(new CartItemDto(
                    item.getId(),
                    p.getId(),
                    p.getName(),
                    p.getSlug(),
                    p.getBrand().getName(),
                    p.getModelName(),
                    p.getMovementType() != null ? p.getMovementType().name() : null,
                    p.getCaseDiameterMm(),
                    v.getId(),
                    v.getSku(),
                    v.getName(),
                    p.getPrimaryImageUrl(),
                    unitRegular,
                    unitSelling,
                    item.getQuantity(),
                    availableStock,
                    maxOrderQuantity,
                    available,
                    lineSubtotal,
                    deliveryEstimationService.estimateForProduct(p, availableStock, null)
            ));
        }

        BigDecimal discount = Money.ZERO;
        String appliedCode = null;
        String couponMsg = null;
        if (cart.getCoupon() != null && subtotal.signum() > 0) {
            try {
                var eval = couponService.evaluate(cart.getCoupon().getCode(), subtotal, userId);
                discount = eval.discountAmount();
                appliedCode = eval.coupon().getCode();
                couponMsg = "Coupon " + appliedCode + " applied";
            } catch (BusinessException ex) {
                cart.setCoupon(null);
                cartRepository.save(cart);
                couponMsg = ex.getMessage();
            }
        }

        TotalsBreakdown totals = calculator.calculate(subtotal, discount);
        return new CartDto(cart.getId(), rawGuestToken, itemDtos, totalQty, appliedCode, couponMsg, totals);
    }

    private CartDto emptyCartDto(String rawGuestToken) {
        return new CartDto(null, rawGuestToken, List.of(), 0, null, null,
                calculator.calculate(Money.ZERO, Money.ZERO));
    }

    private CartHolder resolveCart(Long userId, String rawGuestToken, boolean createIfMissing) {
        if (userId != null) {
            Cart cart = cartRepository.findByUserId(userId).orElse(null);
            if (cart == null && createIfMissing) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
                cart = cartRepository.save(Cart.builder().user(user).build());
            }
            return new CartHolder(cart, null);
        }

        String token = (rawGuestToken != null && !rawGuestToken.isBlank()) ? rawGuestToken.trim() : null;
        if (token != null) {
            String hash = TokenUtils.sha256(token);
            Cart cart = cartRepository.findByGuestToken(hash).orElse(null);
            if (cart == null && createIfMissing) {
                cart = cartRepository.save(Cart.builder().guestToken(hash).build());
            }
            return new CartHolder(cart, token);
        }

        if (!createIfMissing) {
            return new CartHolder(null, null);
        }
        String newRawToken = TokenUtils.generateToken();
        Cart cart = cartRepository.save(Cart.builder().guestToken(TokenUtils.sha256(newRawToken)).build());
        return new CartHolder(cart, newRawToken);
    }

    private record CartHolder(Cart cart, String rawGuestToken) {
    }
}
