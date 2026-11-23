package com.timecraft.product.service;

import com.timecraft.inventory.entity.Inventory;
import com.timecraft.product.dto.ProductDtos.ProductFilterParams;
import com.timecraft.product.entity.Product;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.entity.ProductVariant;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Composable JPA Specification for watch search and multi-facet filtering.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> withFilters(ProductFilterParams f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            predicates.add(cb.isTrue(root.get("brand").get("active")));
            predicates.add(cb.isFalse(root.get("brand").get("deleted")));
            predicates.add(cb.isTrue(root.get("category").get("active")));
            predicates.add(cb.isFalse(root.get("category").get("deleted")));

            if (f == null) {
                return cb.and(predicates.toArray(Predicate[]::new));
            }

            if (notBlank(f.q())) {
                String pattern = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("modelName")), pattern),
                        cb.like(cb.lower(root.get("sku")), pattern),
                        cb.like(cb.lower(root.get("shortDescription")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("brand").get("name")), pattern)
                ));
            }

            if (notBlank(f.brand())) {
                String b = f.brand().trim();
                if (b.chars().allMatch(Character::isDigit)) {
                    predicates.add(cb.equal(root.get("brand").get("id"), Long.valueOf(b)));
                } else {
                    predicates.add(cb.or(
                            cb.equal(cb.lower(root.get("brand").get("slug")), b.toLowerCase(Locale.ROOT)),
                            cb.equal(cb.lower(root.get("brand").get("name")), b.toLowerCase(Locale.ROOT))
                    ));
                }
            }

            if (notBlank(f.category())) {
                String c = f.category().trim();
                if (c.chars().allMatch(Character::isDigit)) {
                    predicates.add(cb.equal(root.get("category").get("id"), Long.valueOf(c)));
                } else {
                    predicates.add(cb.or(
                            cb.equal(cb.lower(root.get("category").get("slug")), c.toLowerCase(Locale.ROOT)),
                            cb.equal(cb.lower(root.get("category").get("name")), c.toLowerCase(Locale.ROOT))
                    ));
                }
            }

            if (f.movement() != null) {
                predicates.add(cb.equal(root.get("movementType"), f.movement()));
            }
            if (f.gender() != null) {
                predicates.add(cb.equal(root.get("gender"), f.gender()));
            }
            if (f.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sellingPrice"), f.minPrice()));
            }
            if (f.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sellingPrice"), f.maxPrice()));
            }
            if (f.minDiameter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("caseDiameterMm"), f.minDiameter()));
            }
            if (f.maxDiameter() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("caseDiameterMm"), f.maxDiameter()));
            }
            if (notBlank(f.strapMaterial())) {
                predicates.add(cb.equal(cb.lower(root.get("strapMaterial")),
                        f.strapMaterial().trim().toLowerCase(Locale.ROOT)));
            }
            if (notBlank(f.dialColor())) {
                predicates.add(cb.equal(cb.lower(root.get("dialColor")),
                        f.dialColor().trim().toLowerCase(Locale.ROOT)));
            }
            if (f.minWaterResistance() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("waterResistanceM"), f.minWaterResistance()));
            }
            if (f.minDiscount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("discountPercent"), f.minDiscount()));
            }
            if (f.minRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("avgRating"), f.minRating()));
            }
            if (Boolean.TRUE.equals(f.featured())) {
                predicates.add(cb.isTrue(root.get("featured")));
            }
            if (Boolean.TRUE.equals(f.newArrival())) {
                predicates.add(cb.isTrue(root.get("newArrival")));
            }
            if (Boolean.TRUE.equals(f.bestSeller())) {
                predicates.add(cb.isTrue(root.get("bestSeller")));
            }
            if (Boolean.TRUE.equals(f.inStock())) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<Inventory> inv = sq.from(Inventory.class);
                Root<ProductVariant> pv = sq.from(ProductVariant.class);
                sq.select(pv.get("product").get("id"))
                        .where(
                                cb.equal(inv.get("variant").get("id"), pv.get("id")),
                                cb.equal(pv.get("product").get("id"), root.get("id")),
                                cb.isFalse(pv.get("deleted")),
                                cb.isTrue(pv.get("active")),
                                cb.greaterThan(cb.diff(inv.get("quantityOnHand"), inv.get("quantityReserved")), 0)
                        );
                predicates.add(cb.exists(sq));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
