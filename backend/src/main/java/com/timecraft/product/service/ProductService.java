package com.timecraft.product.service;

import com.timecraft.audit.service.AuditLogService;
import com.timecraft.brand.entity.Brand;
import com.timecraft.brand.service.BrandService;
import com.timecraft.category.entity.Category;
import com.timecraft.category.service.CategoryService;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.common.util.Money;
import com.timecraft.common.util.SlugUtils;
import com.timecraft.delivery.service.DeliveryEstimationService;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.inventory.service.InventoryService;
import com.timecraft.product.dto.ProductDtos.AttributeDto;
import com.timecraft.product.dto.ProductDtos.AttributeInput;
import com.timecraft.product.dto.ProductDtos.ImageDto;
import com.timecraft.product.dto.ProductDtos.ImageUrlRequest;
import com.timecraft.product.dto.ProductDtos.ProductDetailDto;
import com.timecraft.product.dto.ProductDtos.ProductFilterParams;
import com.timecraft.product.dto.ProductDtos.ProductSummaryDto;
import com.timecraft.product.dto.ProductDtos.ProductUpsertRequest;
import com.timecraft.product.dto.ProductDtos.VariantDto;
import com.timecraft.product.dto.ProductDtos.VariantUpsertRequest;
import com.timecraft.product.entity.MovementType;
import com.timecraft.product.entity.Product;
import com.timecraft.product.entity.ProductAttribute;
import com.timecraft.product.entity.ProductImage;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.entity.ProductVariant;
import com.timecraft.product.entity.WatchGender;
import com.timecraft.product.repository.ProductImageRepository;
import com.timecraft.product.repository.ProductRepository;
import com.timecraft.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int MAX_COMPARE_PRODUCTS = 4;

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final InventoryService inventoryService;
    private final DeliveryEstimationService deliveryEstimationService;
    private final CloudinaryService cloudinaryService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryDto> searchProducts(ProductFilterParams filters, int page, int size) {
        Sort sort = resolveSort(filters != null ? filters.sort() : null);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(48, Math.max(1, size)), sort);
        Page<Product> result = productRepository.findAll(ProductSpecifications.withFilters(filters), pageable);
        return PageResponse.of(result, this::toSummary);
    }

    @Cacheable(value = "productDetails", key = "#slugOrId")
    @Transactional(readOnly = true)
    public ProductDetailDto getActiveBySlugOrId(String slugOrId) {
        Product product = findBySlugOrId(slugOrId, true);
        return toDetail(product);
    }

    @Transactional(readOnly = true)
    public ProductDetailDto getAdminDetail(Long id) {
        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return toDetail(product);
    }

    @Transactional(readOnly = true)
    public List<ProductDetailDto> compareProducts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_COMPARE_PRODUCTS) {
            throw BusinessException.badRequest("COMPARE_LIMIT_EXCEEDED",
                    "You can compare up to " + MAX_COMPARE_PRODUCTS + " timepieces at a time");
        }
        return productRepository.findByIdInAndStatusAndDeletedFalse(ids, ProductStatus.ACTIVE)
                .stream()
                .map(this::toDetail)
                .toList();
    }

    @Cacheable(value = "homeContent", key = "'highlights'")
    @Transactional(readOnly = true)
    public Map<String, List<ProductSummaryDto>> getHomepageCollections() {
        return Map.of(
                "featured", productRepository.findTop8ByStatusAndDeletedFalseAndFeaturedTrueOrderByCreatedAtDesc(ProductStatus.ACTIVE)
                        .stream().map(this::toSummary).toList(),
                "newArrivals", productRepository.findTop8ByStatusAndDeletedFalseAndNewArrivalTrueOrderByCreatedAtDesc(ProductStatus.ACTIVE)
                        .stream().map(this::toSummary).toList(),
                "bestSellers", productRepository.findTop8ByStatusAndDeletedFalseAndBestSellerTrueOrderBySoldCountDesc(ProductStatus.ACTIVE)
                        .stream().map(this::toSummary).toList()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFilterFacets() {
        return Map.of(
                "movements", Arrays.stream(MovementType.values()).map(Enum::name).toList(),
                "genders", Arrays.stream(WatchGender.values()).map(Enum::name).toList(),
                "dialColors", productRepository.findDistinctDialColors(),
                "strapMaterials", productRepository.findDistinctStrapMaterials()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryDto> searchAdmin(String search, ProductStatus status,
                                                       Long brandId, Long categoryId, Pageable pageable) {
        String q = search != null && !search.isBlank() ? search.trim() : null;
        return PageResponse.of(
                productRepository.searchAdmin(q, status, brandId, categoryId, pageable),
                this::toSummary
        );
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public ProductDetailDto createProduct(ProductUpsertRequest req) {
        String sku = req.sku().trim().toUpperCase(Locale.ROOT);
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw BusinessException.conflict("DUPLICATE_SKU", "A product with SKU " + sku + " already exists");
        }
        validatePrices(req.regularPrice(), req.sellingPrice());

        Brand brand = brandService.requireEntity(req.brandId());
        Category category = categoryService.requireEntity(req.categoryId());
        String slug = resolveUniqueSlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : req.name(), null);

        Product product = new Product();
        applyProductFields(product, req, brand, category, sku, slug);
        product = productRepository.save(product);

        // Create default variant and opening inventory
        String defaultVariantSku = sku + "-STD";
        if (variantRepository.existsBySkuIgnoreCase(defaultVariantSku)) {
            defaultVariantSku = sku + "-V1";
        }
        ProductVariant defaultVariant = ProductVariant.builder()
                .product(product)
                .sku(defaultVariantSku)
                .name(product.getDialColor() + " dial / " + product.getStrapMaterial())
                .dialColor(product.getDialColor())
                .strapMaterial(product.getStrapMaterial())
                .displayOrder(0)
                .active(true)
                .build();
        defaultVariant = variantRepository.save(defaultVariant);
        product.getVariants().add(defaultVariant);
        inventoryService.ensureForVariant(defaultVariant, req.initialStock() != null ? req.initialStock() : 10, null);

        if (req.primaryImageUrl() != null && !req.primaryImageUrl().isBlank()) {
            ProductImage img = ProductImage.builder()
                    .product(product)
                    .url(req.primaryImageUrl().trim())
                    .altText(product.getName())
                    .displayOrder(0)
                    .isPrimary(true)
                    .build();
            product.getImages().add(imageRepository.save(img));
        }

        replaceAttributes(product, req.attributes());
        product = productRepository.save(product);

        auditLogService.logCurrentActor("PRODUCT_CREATED", "PRODUCT", product.getId(),
                "Created product " + product.getName() + " (" + product.getSku() + ")");
        return toDetail(product);
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public ProductDetailDto updateProduct(Long id, ProductUpsertRequest req) {
        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        String sku = req.sku().trim().toUpperCase(Locale.ROOT);
        if (!product.getSku().equalsIgnoreCase(sku) && productRepository.existsBySkuIgnoreCase(sku)) {
            throw BusinessException.conflict("DUPLICATE_SKU", "A product with SKU " + sku + " already exists");
        }
        validatePrices(req.regularPrice(), req.sellingPrice());

        Brand brand = brandService.requireEntity(req.brandId());
        Category category = categoryService.requireEntity(req.categoryId());
        String slug = resolveUniqueSlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : req.name(), id);

        applyProductFields(product, req, brand, category, sku, slug);
        if (req.attributes() != null) {
            replaceAttributes(product, req.attributes());
        }
        if (req.primaryImageUrl() != null && !req.primaryImageUrl().isBlank() && product.getImages().isEmpty()) {
            ProductImage img = ProductImage.builder()
                    .product(product)
                    .url(req.primaryImageUrl().trim())
                    .altText(product.getName())
                    .displayOrder(0)
                    .isPrimary(true)
                    .build();
            product.getImages().add(imageRepository.save(img));
        }

        product = productRepository.save(product);
        auditLogService.logCurrentActor("PRODUCT_UPDATED", "PRODUCT", product.getId(),
                "Updated product " + product.getName() + " (" + product.getSku() + ")");
        return toDetail(product);
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setDeleted(true);
        product.setStatus(ProductStatus.ARCHIVED);
        productRepository.save(product);
        auditLogService.logCurrentActor("PRODUCT_DELETED", "PRODUCT", id,
                "Soft-deleted product " + product.getName() + " (" + product.getSku() + ")");
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public VariantDto addVariant(Long productId, VariantUpsertRequest req) {
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        String sku = req.sku().trim().toUpperCase(Locale.ROOT);
        if (variantRepository.existsBySkuIgnoreCase(sku)) {
            throw BusinessException.conflict("DUPLICATE_VARIANT_SKU", "Variant SKU " + sku + " already exists");
        }
        if (req.regularPriceOverride() != null && req.sellingPriceOverride() != null) {
            validatePrices(req.regularPriceOverride(), req.sellingPriceOverride());
        }

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(sku)
                .name(req.name().trim())
                .dialColor(req.dialColor())
                .strapMaterial(req.strapMaterial())
                .strapColor(req.strapColor())
                .regularPriceOverride(req.regularPriceOverride() != null ? Money.of(req.regularPriceOverride()) : null)
                .sellingPriceOverride(req.sellingPriceOverride() != null ? Money.of(req.sellingPriceOverride()) : null)
                .displayOrder(req.displayOrder())
                .active(req.active())
                .build();
        variant = variantRepository.save(variant);
        var inv = inventoryService.ensureForVariant(variant,
                req.initialStock() != null ? req.initialStock() : 0, req.lowStockThreshold());
        auditLogService.logCurrentActor("VARIANT_CREATED", "PRODUCT_VARIANT", variant.getId(),
                "Added variant " + variant.getSku() + " to product " + product.getName());
        return toVariantDto(variant, inv.getAvailableQuantity());
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public VariantDto updateVariant(Long productId, Long variantId, VariantUpsertRequest req) {
        ProductVariant variant = variantRepository.findByIdAndDeletedFalse(variantId)
                .filter(v -> v.getProduct().getId().equals(productId))
                .orElseThrow(() -> new ResourceNotFoundException("Variant", variantId));
        String sku = req.sku().trim().toUpperCase(Locale.ROOT);
        if (!variant.getSku().equalsIgnoreCase(sku) && variantRepository.existsBySkuIgnoreCase(sku)) {
            throw BusinessException.conflict("DUPLICATE_VARIANT_SKU", "Variant SKU " + sku + " already exists");
        }
        variant.setSku(sku);
        variant.setName(req.name().trim());
        variant.setDialColor(req.dialColor());
        variant.setStrapMaterial(req.strapMaterial());
        variant.setStrapColor(req.strapColor());
        variant.setRegularPriceOverride(req.regularPriceOverride() != null ? Money.of(req.regularPriceOverride()) : null);
        variant.setSellingPriceOverride(req.sellingPriceOverride() != null ? Money.of(req.sellingPriceOverride()) : null);
        variant.setDisplayOrder(req.displayOrder());
        variant.setActive(req.active());
        variant = variantRepository.save(variant);
        auditLogService.logCurrentActor("VARIANT_UPDATED", "PRODUCT_VARIANT", variant.getId(),
                "Updated variant " + variant.getSku());
        return toVariantDto(variant, inventoryService.getAvailableForVariant(variant.getId()));
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public void deleteVariant(Long productId, Long variantId) {
        ProductVariant variant = variantRepository.findByIdAndDeletedFalse(variantId)
                .filter(v -> v.getProduct().getId().equals(productId))
                .orElseThrow(() -> new ResourceNotFoundException("Variant", variantId));
        variant.setDeleted(true);
        variant.setActive(false);
        variantRepository.save(variant);
        auditLogService.logCurrentActor("VARIANT_DELETED", "PRODUCT_VARIANT", variantId,
                "Soft-deleted variant " + variant.getSku());
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public ImageDto uploadProductImage(Long productId, MultipartFile file, String altText,
                                       Long variantId, int displayOrder, boolean primary) {
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        var uploaded = cloudinaryService.upload(file, "products/" + product.getSku());
        return saveImageRecord(product, uploaded.url(), uploaded.publicId(), altText, variantId, displayOrder, primary);
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public ImageDto addProductImageByUrl(Long productId, ImageUrlRequest req) {
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        return saveImageRecord(product, req.url().trim(), null, req.altText(),
                req.variantId(), req.displayOrder(), req.primary());
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public ImageDto setPrimaryImage(Long productId, Long imageId) {
        ProductImage image = imageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", imageId));
        imageRepository.clearPrimaryForProduct(productId);
        image.setPrimary(true);
        image = imageRepository.save(image);
        auditLogService.logCurrentActor("PRODUCT_PRIMARY_IMAGE_SET", "PRODUCT_IMAGE", imageId,
                "Set primary image for product " + productId);
        return toImageDto(image);
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public void deleteProductImage(Long productId, Long imageId) {
        ProductImage image = imageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", imageId));
        cloudinaryService.deleteIfConfigured(image.getPublicId());
        imageRepository.delete(image);
        auditLogService.logCurrentActor("PRODUCT_IMAGE_DELETED", "PRODUCT_IMAGE", imageId,
                "Deleted image from product " + productId);
    }

    private ImageDto saveImageRecord(Product product, String url, String publicId, String altText,
                                     Long variantId, int displayOrder, boolean primary) {
        boolean makePrimary = primary || product.getImages().isEmpty();
        if (makePrimary) {
            imageRepository.clearPrimaryForProduct(product.getId());
        }
        ProductVariant variant = variantId != null
                ? variantRepository.findByIdAndDeletedFalse(variantId).orElse(null)
                : null;
        ProductImage img = ProductImage.builder()
                .product(product)
                .variant(variant)
                .url(url)
                .publicId(publicId)
                .altText(altText != null && !altText.isBlank() ? altText.trim() : product.getName())
                .displayOrder(displayOrder)
                .isPrimary(makePrimary)
                .build();
        img = imageRepository.save(img);
        auditLogService.logCurrentActor("PRODUCT_IMAGE_ADDED", "PRODUCT_IMAGE", img.getId(),
                "Added image to product " + product.getName());
        return toImageDto(img);
    }

    private void applyProductFields(Product target, ProductUpsertRequest req,
                                    Brand brand, Category category, String sku, String slug) {
        BigDecimal regular = Money.of(req.regularPrice());
        BigDecimal selling = Money.of(req.sellingPrice());
        target.setBrand(brand);
        target.setCategory(category);
        target.setName(req.name().trim());
        target.setSlug(slug);
        target.setModelName(req.modelName().trim());
        target.setSku(sku);
        target.setShortDescription(req.shortDescription());
        target.setDescription(req.description());
        target.setMovementType(req.movementType());
        target.setCaseDiameterMm(req.caseDiameterMm());
        target.setCaseMaterial(req.caseMaterial().trim());
        target.setWaterResistanceM(req.waterResistanceM());
        target.setCrystalType(req.crystalType().trim());
        target.setStrapMaterial(req.strapMaterial().trim());
        target.setDialColor(req.dialColor().trim());
        target.setCaseColor(req.caseColor());
        target.setGender(req.gender() != null ? req.gender() : WatchGender.UNISEX);
        target.setWarrantyMonths(req.warrantyMonths() > 0 ? req.warrantyMonths() : 24);
        target.setRegularPrice(regular);
        target.setSellingPrice(selling);
        target.setDiscountPercent(Money.discountPercent(regular, selling));
        target.setStatus(req.status() != null ? req.status() : ProductStatus.ACTIVE);
        target.setFeatured(req.featured());
        target.setNewArrival(req.newArrival());
        target.setBestSeller(req.bestSeller());
    }

    private void replaceAttributes(Product product, List<AttributeInput> inputs) {
        product.getAttributes().clear();
        if (inputs == null) {
            return;
        }
        for (AttributeInput in : inputs) {
            if (in != null && in.name() != null && !in.name().isBlank() && in.value() != null && !in.value().isBlank()) {
                product.getAttributes().add(ProductAttribute.builder()
                        .product(product)
                        .attrName(in.name().trim())
                        .attrValue(in.value().trim())
                        .displayOrder(in.displayOrder())
                        .build());
            }
        }
    }

    private static void validatePrices(BigDecimal regular, BigDecimal selling) {
        if (regular.compareTo(BigDecimal.ZERO) < 0 || selling.compareTo(BigDecimal.ZERO) < 0) {
            throw BusinessException.badRequest("INVALID_PRICE", "Prices must be non-negative");
        }
        if (selling.compareTo(regular) > 0) {
            throw BusinessException.badRequest("SELLING_PRICE_EXCEEDS_REGULAR",
                    "Selling price cannot exceed regular price");
        }
    }

    private Product findBySlugOrId(String slugOrId, boolean activeOnly) {
        if (slugOrId.chars().allMatch(Character::isDigit)) {
            Long id = Long.valueOf(slugOrId);
            return (activeOnly
                    ? productRepository.findByIdAndStatusAndDeletedFalse(id, ProductStatus.ACTIVE)
                    : productRepository.findByIdAndDeletedFalse(id))
                    .orElseThrow(() -> new ResourceNotFoundException("Product", slugOrId));
        }
        return (activeOnly
                ? productRepository.findBySlugAndStatusAndDeletedFalse(slugOrId, ProductStatus.ACTIVE)
                : productRepository.findBySlugAndDeletedFalse(slugOrId))
                .orElseThrow(() -> new ResourceNotFoundException("Product", slugOrId));
    }

    private String resolveUniqueSlug(String raw, Long currentId) {
        String base = SlugUtils.slugify(raw);
        if (base.isBlank()) {
            base = "watch";
        }
        String candidate = base;
        int suffix = 1;
        while (true) {
            var existing = productRepository.findBySlugAndDeletedFalse(candidate);
            if (existing.isEmpty() || (currentId != null && existing.get().getId().equals(currentId))) {
                return candidate;
            }
            candidate = base + "-" + (++suffix);
        }
    }

    private static Sort resolveSort(String sortKey) {
        if (sortKey == null) {
            return Sort.by(Sort.Direction.DESC, "featured", "createdAt");
        }
        return switch (sortKey.toLowerCase(Locale.ROOT)) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "sellingPrice");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "sellingPrice");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "popularity", "popular" -> Sort.by(Sort.Direction.DESC, "soldCount", "createdAt");
            case "rating", "highest_rated" -> Sort.by(Sort.Direction.DESC, "avgRating", "reviewCount");
            case "discount" -> Sort.by(Sort.Direction.DESC, "discountPercent");
            default -> Sort.by(Sort.Direction.DESC, "featured", "createdAt");
        };
    }

    public ProductSummaryDto toSummary(Product p) {
        List<ProductVariant> activeVariants = p.getVariants().stream()
                .filter(v -> !v.isDeleted() && v.isActive())
                .toList();
        Map<Long, Integer> stockMap = inventoryService.getAvailableByVariantIds(
                activeVariants.stream().map(ProductVariant::getId).toList());
        int totalStock = stockMap.values().stream().mapToInt(Integer::intValue).sum();
        Long defaultVariantId = activeVariants.stream()
                .filter(v -> stockMap.getOrDefault(v.getId(), 0) > 0)
                .map(ProductVariant::getId)
                .findFirst()
                .orElseGet(() -> activeVariants.isEmpty() ? null : activeVariants.get(0).getId());

        return new ProductSummaryDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getModelName(),
                p.getSku(),
                p.getBrand().getId(),
                p.getBrand().getName(),
                p.getBrand().getSlug(),
                p.getCategory().getId(),
                p.getCategory().getName(),
                p.getCategory().getSlug(),
                p.getShortDescription(),
                p.getMovementType(),
                p.getCaseDiameterMm(),
                p.getCaseMaterial(),
                p.getWaterResistanceM(),
                p.getCrystalType(),
                p.getStrapMaterial(),
                p.getDialColor(),
                p.getGender(),
                p.getWarrantyMonths(),
                p.getRegularPrice(),
                p.getSellingPrice(),
                p.getDiscountPercent(),
                p.getStatus(),
                p.isFeatured(),
                p.isNewArrival(),
                p.isBestSeller(),
                p.getAvgRating(),
                p.getReviewCount(),
                totalStock,
                defaultVariantId,
                p.getPrimaryImageUrl(),
                deliveryEstimationService.estimateForProduct(p, totalStock, null)
        );
    }

    public ProductDetailDto toDetail(Product p) {
        List<ProductVariant> nonDeletedVariants = p.getVariants().stream()
                .filter(v -> !v.isDeleted())
                .toList();
        Map<Long, Integer> stockMap = inventoryService.getAvailableByVariantIds(
                nonDeletedVariants.stream().map(ProductVariant::getId).toList());
        List<VariantDto> variantDtos = nonDeletedVariants.stream()
                .map(v -> toVariantDto(v, stockMap.getOrDefault(v.getId(), 0)))
                .toList();
        int totalStock = variantDtos.stream()
                .filter(VariantDto::active)
                .mapToInt(VariantDto::availableStock)
                .sum();
        List<ImageDto> imageDtos = p.getImages().stream().map(this::toImageDto).toList();
        List<AttributeDto> attrDtos = p.getAttributes().stream()
                .map(a -> new AttributeDto(a.getId(), a.getAttrName(), a.getAttrValue(), a.getDisplayOrder()))
                .toList();

        return new ProductDetailDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getModelName(),
                p.getSku(),
                p.getBrand().getId(),
                p.getBrand().getName(),
                p.getBrand().getSlug(),
                p.getCategory().getId(),
                p.getCategory().getName(),
                p.getCategory().getSlug(),
                p.getShortDescription(),
                p.getDescription(),
                p.getMovementType(),
                p.getCaseDiameterMm(),
                p.getCaseMaterial(),
                p.getWaterResistanceM(),
                p.getCrystalType(),
                p.getStrapMaterial(),
                p.getDialColor(),
                p.getCaseColor(),
                p.getGender(),
                p.getWarrantyMonths(),
                p.getRegularPrice(),
                p.getSellingPrice(),
                p.getDiscountPercent(),
                p.getStatus(),
                p.isFeatured(),
                p.isNewArrival(),
                p.isBestSeller(),
                p.getAvgRating(),
                p.getReviewCount(),
                totalStock,
                variantDtos,
                imageDtos,
                attrDtos,
                p.getCreatedAt(),
                deliveryEstimationService.estimateForProduct(p, totalStock, null)
        );
    }

    private VariantDto toVariantDto(ProductVariant v, int availableStock) {
        return new VariantDto(
                v.getId(),
                v.getSku(),
                v.getName(),
                v.getDialColor(),
                v.getStrapMaterial(),
                v.getStrapColor(),
                v.effectiveRegularPrice(),
                v.effectiveSellingPrice(),
                availableStock,
                v.isActive()
        );
    }

    private ImageDto toImageDto(ProductImage img) {
        return new ImageDto(
                img.getId(),
                img.getVariant() != null ? img.getVariant().getId() : null,
                img.getUrl(),
                img.getPublicId(),
                img.getAltText(),
                img.getDisplayOrder(),
                img.isPrimary()
        );
    }
}
