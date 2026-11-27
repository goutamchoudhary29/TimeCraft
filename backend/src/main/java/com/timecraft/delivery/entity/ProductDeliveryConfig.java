package com.timecraft.delivery.entity;

import com.timecraft.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "product_delivery_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDeliveryConfig {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private DeliveryWarehouse warehouse;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 24)
    private FulfillmentType fulfillmentType = FulfillmentType.IN_STOCK;

    @Column(name = "processing_days_min")
    private Integer processingDaysMin;

    @Column(name = "processing_days_max")
    private Integer processingDaysMax;

    @Column(name = "shipping_days_min_override")
    private Integer shippingDaysMinOverride;

    @Column(name = "shipping_days_max_override")
    private Integer shippingDaysMaxOverride;

    @Column(name = "preorder_release_date")
    private LocalDate preorderReleaseDate;

    @Column(name = "special_order_note", length = 255)
    private String specialOrderNote;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
