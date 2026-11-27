package com.timecraft.cart.repository;

import com.timecraft.cart.entity.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product", "items.variant.product.brand"})
    Optional<Cart> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product", "items.variant.product.brand"})
    Optional<Cart> findByGuestToken(String guestToken);
}
