package com.timecraft.product.repository;

import com.timecraft.product.entity.Product;
import com.timecraft.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Override
    @EntityGraph(attributePaths = {"brand", "category"})
    Page<Product> findAll(@Nullable Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "category"})
    Optional<Product> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"brand", "category"})
    Optional<Product> findBySlugAndDeletedFalse(String slug);

    @EntityGraph(attributePaths = {"brand", "category"})
    Optional<Product> findByIdAndStatusAndDeletedFalse(Long id, ProductStatus status);

    @EntityGraph(attributePaths = {"brand", "category"})
    Optional<Product> findBySlugAndStatusAndDeletedFalse(String slug, ProductStatus status);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = {"brand", "category"})
    List<Product> findTop8ByStatusAndDeletedFalseAndFeaturedTrueOrderByCreatedAtDesc(ProductStatus status);

    @EntityGraph(attributePaths = {"brand", "category"})
    List<Product> findTop8ByStatusAndDeletedFalseAndNewArrivalTrueOrderByCreatedAtDesc(ProductStatus status);

    @EntityGraph(attributePaths = {"brand", "category"})
    List<Product> findTop8ByStatusAndDeletedFalseAndBestSellerTrueOrderBySoldCountDesc(ProductStatus status);

    @EntityGraph(attributePaths = {"brand", "category"})
    List<Product> findByIdInAndStatusAndDeletedFalse(List<Long> ids, ProductStatus status);

    long countByDeletedFalse();

    long countByStatusAndDeletedFalse(ProductStatus status);

    @Query("SELECT DISTINCT p.dialColor FROM Product p WHERE p.deleted = false AND p.status = 'ACTIVE' ORDER BY p.dialColor")
    List<String> findDistinctDialColors();

    @Query("SELECT DISTINCT p.strapMaterial FROM Product p WHERE p.deleted = false AND p.status = 'ACTIVE' ORDER BY p.strapMaterial")
    List<String> findDistinctStrapMaterials();

    @EntityGraph(attributePaths = {"brand", "category"})
    @Query("""
            SELECT p FROM Product p
            WHERE p.deleted = false
            AND (:status IS NULL OR p.status = :status)
            AND (:brandId IS NULL OR p.brand.id = :brandId)
            AND (:categoryId IS NULL OR p.category.id = :categoryId)
            AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(p.modelName) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Product> searchAdmin(
            @Param("search") String search,
            @Param("status") ProductStatus status,
            @Param("brandId") Long brandId,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );
}
