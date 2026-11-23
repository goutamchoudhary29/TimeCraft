package com.timecraft.inventory.repository;

import com.timecraft.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByVariantId(Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variant.id = :variantId")
    Optional<Inventory> findByVariantIdForUpdate(@Param("variantId") Long variantId);

    List<Inventory> findByVariantIdIn(Collection<Long> variantIds);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.variant v JOIN FETCH v.product p WHERE p.id = :productId AND v.deleted = false")
    List<Inventory> findByProductId(@Param("productId") Long productId);

    @Query("""
            SELECT i FROM Inventory i
            JOIN FETCH i.variant v
            JOIN FETCH v.product p
            WHERE p.deleted = false AND v.deleted = false
            AND (i.quantityOnHand - i.quantityReserved) <= i.lowStockThreshold
            ORDER BY (i.quantityOnHand - i.quantityReserved) ASC
            """)
    List<Inventory> findLowStockItems();

    @Query("""
            SELECT COUNT(i) FROM Inventory i
            WHERE i.variant.deleted = false AND i.variant.product.deleted = false
            AND (i.quantityOnHand - i.quantityReserved) <= i.lowStockThreshold
            """)
    long countLowStock();

    @Query("""
            SELECT i FROM Inventory i
            JOIN i.variant v
            JOIN v.product p
            WHERE p.deleted = false AND v.deleted = false
            AND (:lowOnly = false OR (i.quantityOnHand - i.quantityReserved) <= i.lowStockThreshold)
            AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(v.sku) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Inventory> searchInventory(@Param("search") String search, @Param("lowOnly") boolean lowOnly, Pageable pageable);
}
