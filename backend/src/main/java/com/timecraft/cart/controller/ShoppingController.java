package com.timecraft.cart.controller;

import com.timecraft.cart.service.CartService;
import com.timecraft.cart.service.CartService.AddCartItemRequest;
import com.timecraft.cart.service.CartService.ApplyCouponRequest;
import com.timecraft.cart.service.CartService.CartDto;
import com.timecraft.cart.service.CartService.UpdateCartItemRequest;
import com.timecraft.security.SecurityUtils;
import com.timecraft.security.TimeCraftPrincipal;
import com.timecraft.wishlist.service.WishlistService;
import com.timecraft.wishlist.service.WishlistService.AddWishlistItemRequest;
import com.timecraft.wishlist.service.WishlistService.WishlistItemDto;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ShoppingController {

    private static final String GUEST_HDR = "X-Guest-Token";

    private final CartService cartSvc;
    private final WishlistService wishSvc;

    @GetMapping("/cart")
    public CartDto viewCart(@RequestHeader(value = GUEST_HDR, required = false) String guest,
                            HttpServletResponse resp) {
        return attachGuestHeader(cartSvc.getCart(currentUid(), guest), resp);
    }

    @PostMapping("/cart/items")
    public CartDto addToCart(@RequestHeader(value = GUEST_HDR, required = false) String guest,
                             @Valid @RequestBody AddCartItemRequest body,
                             HttpServletResponse resp) {
        return attachGuestHeader(cartSvc.addItem(currentUid(), guest, body), resp);
    }

    @PutMapping("/cart/items/{id}")
    public CartDto changeQty(@RequestHeader(value = GUEST_HDR, required = false) String guest,
                             @PathVariable Long id,
                             @Valid @RequestBody UpdateCartItemRequest body,
                             HttpServletResponse resp) {
        return attachGuestHeader(cartSvc.updateItemQuantity(currentUid(), guest, id, body), resp);
    }

    @DeleteMapping("/cart/items/{id}")
    public CartDto dropCartItem(@RequestHeader(value = GUEST_HDR, required = false) String guest,
                                @PathVariable Long id,
                                HttpServletResponse resp) {
        return attachGuestHeader(cartSvc.removeItem(currentUid(), guest, id), resp);
    }

    @PostMapping("/cart/coupon")
    public CartDto applyVoucher(@RequestHeader(value = GUEST_HDR, required = false) String guest,
                                @Valid @RequestBody ApplyCouponRequest body,
                                HttpServletResponse resp) {
        return attachGuestHeader(cartSvc.applyCoupon(currentUid(), guest, body.code()), resp);
    }

    @DeleteMapping("/cart/coupon")
    public CartDto clearVoucher(@RequestHeader(value = GUEST_HDR, required = false) String guest,
                                HttpServletResponse resp) {
        return attachGuestHeader(cartSvc.removeCoupon(currentUid(), guest), resp);
    }

    @GetMapping("/wishlist")
    public List<WishlistItemDto> viewWishlist() {
        return wishSvc.getWishlist(SecurityUtils.requireUserId());
    }

    @PostMapping("/wishlist/items")
    public List<WishlistItemDto> pinToWishlist(@Valid @RequestBody AddWishlistItemRequest body) {
        return wishSvc.addItem(SecurityUtils.requireUserId(), body);
    }

    @DeleteMapping("/wishlist/items/{id}")
    public List<WishlistItemDto> unpinFromWishlist(@PathVariable Long id) {
        return wishSvc.removeItem(SecurityUtils.requireUserId(), id);
    }

    @PostMapping("/wishlist/items/{id}/move-to-cart")
    public CartDto transferToCart(@PathVariable Long id) {
        return wishSvc.moveToCart(SecurityUtils.requireUserId(), id);
    }

    private static Long currentUid() {
        return SecurityUtils.currentPrincipal().map(TimeCraftPrincipal::getId).orElse(null);
    }

    private static CartDto attachGuestHeader(CartDto dto, HttpServletResponse resp) {
        if (dto.guestToken() != null) {
            resp.setHeader(GUEST_HDR, dto.guestToken());
        }
        return dto;
    }
}
