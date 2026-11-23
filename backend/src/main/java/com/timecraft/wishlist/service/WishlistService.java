package com.timecraft.wishlist.service;

import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.cart.service.CartService;
import com.timecraft.cart.service.CartService.AddCartItemRequest;
import com.timecraft.cart.service.CartService.CartDto;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.inventory.service.InventoryService;
import com.timecraft.product.entity.Product;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.entity.ProductVariant;
import com.timecraft.product.repository.ProductRepository;
import com.timecraft.product.repository.ProductVariantRepository;
import com.timecraft.wishlist.entity.Wishlist;
import com.timecraft.wishlist.entity.WishlistItem;
import com.timecraft.wishlist.repository.WishlistItemRepository;
import com.timecraft.wishlist.repository.WishlistRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final CartService cartService;

    public record AddWishlistItemRequest(@NotNull Long productId, Long variantId) {
    }

    public record WishlistItemDto(
            Long id,
            Long productId,
            String productName,
            String productSlug,
            String brandName,
            Long variantId,
            String variantName,
            String imageUrl,
            BigDecimal regularPrice,
            BigDecimal sellingPrice,
            BigDecimal discountPercent,
            boolean available,
            int availableStock,
            Instant addedAt
    ) {
    }

    @Transactional(readOnly = true)
    public List<WishlistItemDto> getWishlist(Long userId) {
        return wishlistRepository.findByUserId(userId)
                .map(w -> w.getItems().stream().map(this::toDto).toList())
                .orElse(List.of());
    }

    @Transactional
    public List<WishlistItemDto> addItem(Long userId, AddWishlistItemRequest req) {
        Wishlist wishlist = getOrCreateWishlist(userId);
        var existing = wishlistItemRepository.findByWishlistIdAndProductId(wishlist.getId(), req.productId());
        if (existing.isPresent()) {
            return wishlist.getItems().stream().map(this::toDto).toList();
        }

        Product product = productRepository.findByIdAndDeletedFalse(req.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", req.productId()));
        ProductVariant variant = resolveVariant(product, req.variantId());

        WishlistItem item = WishlistItem.builder()
                .wishlist(wishlist)
                .product(product)
                .variant(variant)
                .build();
        wishlist.getItems().add(0, wishlistItemRepository.save(item));
        return wishlist.getItems().stream().map(this::toDto).toList();
    }

    @Transactional
    public List<WishlistItemDto> removeItem(Long userId, Long itemId) {
        Wishlist wishlist = getOrCreateWishlist(userId);
        WishlistItem item = wishlistItemRepository.findByIdAndWishlistId(itemId, wishlist.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WishlistItem", itemId));
        wishlist.getItems().remove(item);
        wishlistItemRepository.delete(item);
        return wishlist.getItems().stream().map(this::toDto).toList();
    }

    @Transactional
    public CartDto moveToCart(Long userId, Long itemId) {
        Wishlist wishlist = getOrCreateWishlist(userId);
        WishlistItem item = wishlistItemRepository.findByIdAndWishlistId(itemId, wishlist.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WishlistItem", itemId));
        ProductVariant variant = resolveVariant(item.getProduct(),
                item.getVariant() != null ? item.getVariant().getId() : null);
        if (variant == null) {
            throw new ResourceNotFoundException("Variant for product", item.getProduct().getId());
        }
        CartDto cart = cartService.addItem(userId, null, new AddCartItemRequest(variant.getId(), 1));
        wishlist.getItems().remove(item);
        wishlistItemRepository.delete(item);
        return cart;
    }

    private Wishlist getOrCreateWishlist(Long userId) {
        return wishlistRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));
            return wishlistRepository.save(Wishlist.builder().user(user).build());
        });
    }

    private ProductVariant resolveVariant(Product product, Long variantId) {
        if (variantId != null) {
            return variantRepository.findByIdAndDeletedFalse(variantId)
                    .filter(v -> v.getProduct().getId().equals(product.getId()))
                    .orElse(null);
        }
        return product.getVariants().stream()
                .filter(v -> !v.isDeleted() && v.isActive())
                .findFirst()
                .orElse(null);
    }

    private WishlistItemDto toDto(WishlistItem item) {
        Product p = item.getProduct();
        ProductVariant v = resolveVariant(p, item.getVariant() != null ? item.getVariant().getId() : null);
        int stock = v != null ? inventoryService.getAvailableForVariant(v.getId()) : 0;
        boolean available = !p.isDeleted() && p.getStatus() == ProductStatus.ACTIVE
                && v != null && v.isActive() && !v.isDeleted() && stock > 0;
        return new WishlistItemDto(
                item.getId(),
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getBrand().getName(),
                v != null ? v.getId() : null,
                v != null ? v.getName() : null,
                p.getPrimaryImageUrl(),
                v != null ? v.effectiveRegularPrice() : p.getRegularPrice(),
                v != null ? v.effectiveSellingPrice() : p.getSellingPrice(),
                p.getDiscountPercent(),
                available,
                stock,
                item.getCreatedAt()
        );
    }
}
