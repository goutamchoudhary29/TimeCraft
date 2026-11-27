package com.timecraft.product.dto;

import com.timecraft.delivery.dto.DeliveryDtos.DeliveryEstimateDto;
import com.timecraft.product.entity.MovementType;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.entity.WatchGender;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductFilterParams(
            String q,
            String brand,
            String category,
            MovementType movement,
            WatchGender gender,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minDiameter,
            BigDecimal maxDiameter,
            String strapMaterial,
            String dialColor,
            Integer minWaterResistance,
            Boolean inStock,
            BigDecimal minDiscount,
            BigDecimal minRating,
            Boolean featured,
            Boolean newArrival,
            Boolean bestSeller,
            String sort
    ) {
    }

    public record VariantDto(
            Long id,
            String sku,
            String name,
            String dialColor,
            String strapMaterial,
            String strapColor,
            BigDecimal regularPrice,
            BigDecimal sellingPrice,
            int availableStock,
            boolean active
    ) {
    }

    public record ImageDto(
            Long id,
            Long variantId,
            String url,
            String publicId,
            String altText,
            int displayOrder,
            boolean primary
    ) {
    }

    public record AttributeDto(
            Long id,
            String name,
            String value,
            int displayOrder
    ) {
    }

    public record ProductSummaryDto(
            Long id,
            String name,
            String slug,
            String modelName,
            String sku,
            Long brandId,
            String brandName,
            String brandSlug,
            Long categoryId,
            String categoryName,
            String categorySlug,
            String shortDescription,
            MovementType movementType,
            BigDecimal caseDiameterMm,
            String caseMaterial,
            int waterResistanceM,
            String crystalType,
            String strapMaterial,
            String dialColor,
            WatchGender gender,
            int warrantyMonths,
            BigDecimal regularPrice,
            BigDecimal sellingPrice,
            BigDecimal discountPercent,
            ProductStatus status,
            boolean featured,
            boolean newArrival,
            boolean bestSeller,
            BigDecimal avgRating,
            int reviewCount,
            int totalAvailableStock,
            Long defaultVariantId,
            String primaryImageUrl,
            DeliveryEstimateDto deliveryEstimate
    ) {
    }

    public record ProductDetailDto(
            Long id,
            String name,
            String slug,
            String modelName,
            String sku,
            Long brandId,
            String brandName,
            String brandSlug,
            Long categoryId,
            String categoryName,
            String categorySlug,
            String shortDescription,
            String description,
            MovementType movementType,
            BigDecimal caseDiameterMm,
            String caseMaterial,
            int waterResistanceM,
            String crystalType,
            String strapMaterial,
            String dialColor,
            String caseColor,
            WatchGender gender,
            int warrantyMonths,
            BigDecimal regularPrice,
            BigDecimal sellingPrice,
            BigDecimal discountPercent,
            ProductStatus status,
            boolean featured,
            boolean newArrival,
            boolean bestSeller,
            BigDecimal avgRating,
            int reviewCount,
            int totalAvailableStock,
            List<VariantDto> variants,
            List<ImageDto> images,
            List<AttributeDto> attributes,
            Instant createdAt,
            DeliveryEstimateDto deliveryEstimate
    ) {
    }

    public record ProductUpsertRequest(
            @NotNull Long brandId,
            @NotNull Long categoryId,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 180) String slug,
            @NotBlank @Size(max = 120) String modelName,
            @NotBlank @Size(max = 64) String sku,
            @Size(max = 300) String shortDescription,
            String description,
            @NotNull MovementType movementType,
            @NotNull @DecimalMin("15.0") BigDecimal caseDiameterMm,
            @NotBlank @Size(max = 60) String caseMaterial,
            @Min(0) int waterResistanceM,
            @NotBlank @Size(max = 60) String crystalType,
            @NotBlank @Size(max = 60) String strapMaterial,
            @NotBlank @Size(max = 40) String dialColor,
            @Size(max = 40) String caseColor,
            WatchGender gender,
            @Min(0) int warrantyMonths,
            @NotNull @DecimalMin("0.0") BigDecimal regularPrice,
            @NotNull @DecimalMin("0.0") BigDecimal sellingPrice,
            ProductStatus status,
            boolean featured,
            boolean newArrival,
            boolean bestSeller,
            String primaryImageUrl,
            Integer initialStock,
            List<AttributeInput> attributes
    ) {
    }

    public record AttributeInput(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 255) String value,
            int displayOrder
    ) {
    }

    public record VariantUpsertRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 40) String dialColor,
            @Size(max = 60) String strapMaterial,
            @Size(max = 40) String strapColor,
            BigDecimal regularPriceOverride,
            BigDecimal sellingPriceOverride,
            int displayOrder,
            boolean active,
            Integer initialStock,
            Integer lowStockThreshold
    ) {
    }

    public record ImageUrlRequest(
            @NotBlank @Size(max = 500) String url,
            @Size(max = 200) String altText,
            Long variantId,
            int displayOrder,
            boolean primary
    ) {
    }
}
