package com.timecraft.product.controller;

import com.timecraft.brand.service.BrandService;
import com.timecraft.brand.service.BrandService.BrandDto;
import com.timecraft.category.service.CategoryService;
import com.timecraft.category.service.CategoryService.CategoryDto;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.product.dto.ProductDtos.ProductDetailDto;
import com.timecraft.product.dto.ProductDtos.ProductFilterParams;
import com.timecraft.product.dto.ProductDtos.ProductSummaryDto;
import com.timecraft.product.entity.MovementType;
import com.timecraft.product.entity.WatchGender;
import com.timecraft.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Catalog & Product Discovery")
public class CatalogController {

    private final ProductService productService;
    private final BrandService brandService;
    private final CategoryService categoryService;

    @GetMapping({"/products", "/products/search"})
    @Operation(summary = "Search, filter and sort watches with pagination")
    public ResponseEntity<PageResponse<ProductSummaryDto>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) MovementType movement,
            @RequestParam(required = false) WatchGender gender,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minDiameter,
            @RequestParam(required = false) BigDecimal maxDiameter,
            @RequestParam(required = false) String strapMaterial,
            @RequestParam(required = false) String dialColor,
            @RequestParam(required = false) Integer minWaterResistance,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) BigDecimal minDiscount,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) Boolean newArrival,
            @RequestParam(required = false) Boolean bestSeller,
            @RequestParam(defaultValue = "featured") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        ProductFilterParams params = new ProductFilterParams(
                q, brand, category, movement, gender, minPrice, maxPrice,
                minDiameter, maxDiameter, strapMaterial, dialColor, minWaterResistance,
                inStock, minDiscount, minRating, featured, newArrival, bestSeller, sort
        );
        return ResponseEntity.ok(productService.searchProducts(params, page, size));
    }

    @GetMapping("/products/collections")
    @Operation(summary = "Featured, new arrival and best-seller collections for the homepage")
    public ResponseEntity<Map<String, List<ProductSummaryDto>>> collections() {
        return ResponseEntity.ok(productService.getHomepageCollections());
    }

    @GetMapping("/products/facets")
    @Operation(summary = "Available watch filter facets (movements, dial colors, strap materials)")
    public ResponseEntity<Map<String, Object>> facets() {
        return ResponseEntity.ok(productService.getFilterFacets());
    }

    @GetMapping("/products/compare")
    @Operation(summary = "Compare up to 4 watches side by side")
    public ResponseEntity<List<ProductDetailDto>> compare(@RequestParam("ids") List<Long> ids) {
        return ResponseEntity.ok(productService.compareProducts(ids));
    }

    @GetMapping("/products/{slugOrId}")
    @Operation(summary = "Get complete watch details, variants, images and specifications")
    public ResponseEntity<ProductDetailDto> getProduct(@PathVariable String slugOrId) {
        return ResponseEntity.ok(productService.getActiveBySlugOrId(slugOrId));
    }

    @GetMapping("/brands")
    public ResponseEntity<List<BrandDto>> listBrands(@RequestParam(required = false) Boolean featured) {
        return ResponseEntity.ok(Boolean.TRUE.equals(featured)
                ? brandService.listFeatured()
                : brandService.listActive());
    }

    @GetMapping("/brands/{slugOrId}")
    public ResponseEntity<BrandDto> getBrand(@PathVariable String slugOrId) {
        return ResponseEntity.ok(brandService.getBySlugOrId(slugOrId));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> listCategories() {
        return ResponseEntity.ok(categoryService.listActive());
    }

    @GetMapping("/categories/{slugOrId}")
    public ResponseEntity<CategoryDto> getCategory(@PathVariable String slugOrId) {
        return ResponseEntity.ok(categoryService.getBySlugOrId(slugOrId));
    }
}
