package com.timecraft.delivery.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.timecraft.delivery.entity.DeliveryZoneType;
import com.timecraft.delivery.entity.FulfillmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    public record DeliveryEstimateDto(
            boolean serviceable,
            boolean available,
            String stockCondition,
            String fulfillmentType,
            String zoneType,
            String pinCode,
            String warehouseCode,
            String warehouseName,
            String warehouseCity,
            String warehouseState,
            String courierName,
            int processingDaysMin,
            int processingDaysMax,
            int shippingDaysMin,
            int shippingDaysMax,
            int totalBusinessDaysMin,
            int totalBusinessDaysMax,
            LocalDate expectedDispatchDate,
            LocalDate estimatedDeliveryStartDate,
            LocalDate estimatedDeliveryEndDate,
            LocalDate preorderReleaseDate,
            String shortLabel,
            String detailMessage,
            String shippingInfo
    ) {
    }

    public record CartItemDeliveryDto(
            Long cartItemId,
            Long productId,
            String productName,
            String brandName,
            DeliveryEstimateDto estimate
    ) {
    }

    public record CartDeliverySummaryDto(
            String pinCode,
            boolean splitWarehouseShipment,
            int warehouseCount,
            List<String> warehouseNames,
            String summaryNote,
            List<CartItemDeliveryDto> items
    ) {
    }

    public record ProductDeliveryConfigDto(
            Long productId,
            String productName,
            String brandName,
            String sku,
            Long warehouseId,
            String warehouseName,
            FulfillmentType fulfillmentType,
            Integer processingDaysMin,
            Integer processingDaysMax,
            Integer shippingDaysMinOverride,
            Integer shippingDaysMaxOverride,
            LocalDate preorderReleaseDate,
            String specialOrderNote
    ) {
    }

    public record WarehouseRequest(
            @JsonProperty("code") @NotBlank @Size(max = 40) String code,
            @JsonProperty("name") @NotBlank @Size(max = 120) String name,
            @JsonProperty("city") @NotBlank @Size(max = 80) String city,
            @JsonProperty("state") @NotBlank @Size(max = 80) String state,
            @JsonProperty("pinCode") @NotBlank @Size(max = 12) String pinCode,
            @JsonProperty("pinPrefixLocal") @Size(max = 160) String pinPrefixLocal,
            @JsonProperty("pinPrefixState") @Size(max = 255) String pinPrefixState,
            @JsonProperty("defaultWarehouse") boolean defaultWarehouse,
            @JsonProperty("active") boolean active
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public WarehouseRequest {
        }
    }

    public record PinRuleRequest(
            @JsonProperty("pinPattern") @NotBlank @Size(max = 20) String pinPattern,
            @JsonProperty("zoneType") @NotNull DeliveryZoneType zoneType,
            @JsonProperty("courierName") @Size(max = 80) String courierName,
            @JsonProperty("serviceable") boolean serviceable,
            @JsonProperty("minDaysOverride") Integer minDaysOverride,
            @JsonProperty("maxDaysOverride") Integer maxDaysOverride,
            @JsonProperty("notes") @Size(max = 255) String notes
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public PinRuleRequest {
        }
    }

    public record HolidayRequest(
            @JsonProperty("holidayDate") @NotNull LocalDate holidayDate,
            @JsonProperty("name") @NotBlank @Size(max = 120) String name
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public HolidayRequest {
        }
    }

    public record ProductDeliveryConfigRequest(
            @JsonProperty("warehouseId") Long warehouseId,
            @JsonProperty("fulfillmentType") @NotNull FulfillmentType fulfillmentType,
            @JsonProperty("processingDaysMin") Integer processingDaysMin,
            @JsonProperty("processingDaysMax") Integer processingDaysMax,
            @JsonProperty("shippingDaysMinOverride") Integer shippingDaysMinOverride,
            @JsonProperty("shippingDaysMaxOverride") Integer shippingDaysMaxOverride,
            @JsonProperty("preorderReleaseDate") LocalDate preorderReleaseDate,
            @JsonProperty("specialOrderNote") @Size(max = 255) String specialOrderNote
    ) {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public ProductDeliveryConfigRequest {
        }
    }
}
