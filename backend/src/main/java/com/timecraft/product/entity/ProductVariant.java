package com.timecraft.product.entity;

import com.timecraft.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "dial_color", length = 40)
    private String dialColor;

    @Column(name = "strap_material", length = 60)
    private String strapMaterial;

    @Column(name = "strap_color", length = 40)
    private String strapColor;

    @Column(name = "regular_price_override", precision = 12, scale = 2)
    private BigDecimal regularPriceOverride;

    @Column(name = "selling_price_override", precision = 12, scale = 2)
    private BigDecimal sellingPriceOverride;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    public BigDecimal effectiveRegularPrice() {
        return regularPriceOverride != null ? regularPriceOverride : product.getRegularPrice();
    }

    public BigDecimal effectiveSellingPrice() {
        return sellingPriceOverride != null ? sellingPriceOverride : product.getSellingPrice();
    }
}
