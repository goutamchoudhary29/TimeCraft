package com.timecraft.delivery.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "delivery_pin_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryPinRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pin_pattern", nullable = false, unique = true, length = 20)
    private String pinPattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 24)
    private DeliveryZoneType zoneType;

    @Column(name = "courier_name", length = 80)
    private String courierName;

    @Builder.Default
    @Column(name = "serviceable", nullable = false)
    private boolean serviceable = true;

    @Column(name = "min_days_override")
    private Integer minDaysOverride;

    @Column(name = "max_days_override")
    private Integer maxDaysOverride;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
