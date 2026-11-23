package com.timecraft.product.repository;

import com.timecraft.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByIsPrimaryDescDisplayOrderAscIdAsc(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    @Modifying
    @Query("UPDATE ProductImage img SET img.isPrimary = false WHERE img.product.id = :productId")
    void clearPrimaryForProduct(@Param("productId") Long productId);
}
