package com.timecraft.product.repository;

import com.timecraft.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductIdAndDeletedFalseOrderByDisplayOrderAscIdAsc(Long productId);

    Optional<ProductVariant> findByIdAndDeletedFalse(Long id);

    boolean existsBySkuIgnoreCase(String sku);
}
