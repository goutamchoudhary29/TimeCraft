package com.timecraft.product.entity;

import com.timecraft.brand.entity.Brand;
import com.timecraft.category.entity.Category;
import com.timecraft.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 180)
    private String slug;

    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;

    @Column(name = "sku", nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "short_description", length = 300)
    private String shortDescription;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Column(name = "case_diameter_mm", nullable = false, precision = 5, scale = 2)
    private BigDecimal caseDiameterMm;

    @Column(name = "case_material", nullable = false, length = 60)
    private String caseMaterial;

    @Builder.Default
    @Column(name = "water_resistance_m", nullable = false)
    private int waterResistanceM = 30;

    @Column(name = "crystal_type", nullable = false, length = 60)
    private String crystalType;

    @Column(name = "strap_material", nullable = false, length = 60)
    private String strapMaterial;

    @Column(name = "dial_color", nullable = false, length = 40)
    private String dialColor;

    @Column(name = "case_color", length = 40)
    private String caseColor;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private WatchGender gender = WatchGender.UNISEX;

    @Builder.Default
    @Column(name = "warranty_months", nullable = false)
    private int warrantyMonths = 24;

    @Column(name = "regular_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal regularPrice;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Builder.Default
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @Builder.Default
    @Column(name = "featured", nullable = false)
    private boolean featured = false;

    @Builder.Default
    @Column(name = "new_arrival", nullable = false)
    private boolean newArrival = false;

    @Builder.Default
    @Column(name = "best_seller", nullable = false)
    private boolean bestSeller = false;

    @Builder.Default
    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Builder.Default
    @Column(name = "sold_count", nullable = false)
    private int soldCount = 0;

    @Builder.Default
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("isPrimary DESC, displayOrder ASC, id ASC")
    private List<ProductImage> images = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<ProductAttribute> attributes = new ArrayList<>();

    public String getPrimaryImageUrl() {
        if (images == null || images.isEmpty()) {
            return "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=1200&q=80";
        }
        return images.stream()
                .filter(ProductImage::isPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(images.get(0).getUrl());
    }
}
