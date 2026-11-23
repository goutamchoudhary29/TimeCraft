package com.timecraft.admin.controller;

import com.timecraft.admin.entity.StoreSetting;
import com.timecraft.admin.entity.SupportTicketStatus;
import com.timecraft.admin.service.StoreConfigService;
import com.timecraft.admin.service.StoreConfigService.SupportTicketDto;
import com.timecraft.audit.entity.AuditLog;
import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.entity.AccountStatus;
import com.timecraft.brand.service.BrandService;
import com.timecraft.brand.service.BrandService.BrandDto;
import com.timecraft.brand.service.BrandService.BrandRequest;
import com.timecraft.category.service.CategoryService;
import com.timecraft.category.service.CategoryService.CategoryDto;
import com.timecraft.category.service.CategoryService.CategoryRequest;
import com.timecraft.common.dto.MessageResponse;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.coupon.service.CouponService;
import com.timecraft.coupon.service.CouponService.CouponDto;
import com.timecraft.coupon.service.CouponService.CouponRequest;
import com.timecraft.customer.service.CustomerService;
import com.timecraft.customer.service.CustomerService.ProfileDto;
import com.timecraft.inventory.service.InventoryService;
import com.timecraft.inventory.service.InventoryService.InventoryAdjustmentRequest;
import com.timecraft.inventory.service.InventoryService.InventoryDto;
import com.timecraft.inventory.service.InventoryService.InventoryTransactionDto;
import com.timecraft.order.dto.OrderDtos.OrderDetailDto;
import com.timecraft.order.dto.OrderDtos.OrderSummaryDto;
import com.timecraft.order.dto.OrderDtos.UpdateOrderStatusRequest;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.PaymentStatus;
import com.timecraft.order.service.OrderService;
import com.timecraft.payment.service.RazorpayPaymentService;
import com.timecraft.payment.service.RazorpayPaymentService.PaymentDto;
import com.timecraft.product.dto.ProductDtos.ImageDto;
import com.timecraft.product.dto.ProductDtos.ImageUrlRequest;
import com.timecraft.product.dto.ProductDtos.ProductDetailDto;
import com.timecraft.product.dto.ProductDtos.ProductSummaryDto;
import com.timecraft.product.dto.ProductDtos.ProductUpsertRequest;
import com.timecraft.product.dto.ProductDtos.VariantDto;
import com.timecraft.product.dto.ProductDtos.VariantUpsertRequest;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.service.ProductService;
import com.timecraft.report.service.ReportService;
import com.timecraft.report.service.ReportService.DashboardOverview;
import com.timecraft.report.service.ReportService.SalesReport;
import com.timecraft.returns.entity.ReturnRequestStatus;
import com.timecraft.returns.service.ReturnAndRefundService;
import com.timecraft.returns.service.ReturnAndRefundService.AdminDecisionRequest;
import com.timecraft.returns.service.ReturnAndRefundService.InitiateRefundRequest;
import com.timecraft.returns.service.ReturnAndRefundService.RefundDto;
import com.timecraft.returns.service.ReturnAndRefundService.ReturnRequestDto;
import com.timecraft.review.entity.ReviewStatus;
import com.timecraft.review.service.ReviewService;
import com.timecraft.review.service.ReviewService.ModerateReviewRequest;
import com.timecraft.review.service.ReviewService.ReviewDto;
import com.timecraft.shipment.service.ShipmentService;
import com.timecraft.shipment.service.ShipmentService.ShipmentDto;
import com.timecraft.shipment.service.ShipmentService.UpsertShipmentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints under /api/v1/admin/** protected by the stateful ADMIN session filter chain
 * (requires ROLE_ADMIN and CSRF token for state-changing operations).
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminApiController {

    private final ReportService reportService;
    private final ProductService productService;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final RazorpayPaymentService paymentService;
    private final CouponService couponService;
    private final ShipmentService shipmentService;
    private final ReviewService reviewService;
    private final ReturnAndRefundService returnService;
    private final CustomerService customerService;
    private final StoreConfigService storeConfigService;
    private final AuditLogService auditLogService;

    @GetMapping("/dashboard")
    public DashboardOverview dashboard() {
        return reportService.getDashboardOverview();
    }

    @GetMapping("/reports/sales")
    public SalesReport salesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.generateSalesReport(from, to);
    }

    @GetMapping("/products")
    public PageResponse<ProductSummaryDto> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return productService.searchAdmin(search, status, brandId, categoryId,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/products/{id}")
    public ProductDetailDto getProduct(@PathVariable Long id) {
        return productService.getAdminDetail(id);
    }

    @PostMapping("/products")
    public ResponseEntity<ProductDetailDto> createProduct(@Valid @RequestBody ProductUpsertRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(body));
    }

    @PutMapping("/products/{id}")
    public ProductDetailDto updateProduct(@PathVariable Long id, @Valid @RequestBody ProductUpsertRequest body) {
        return productService.updateProduct(id, body);
    }

    @DeleteMapping("/products/{id}")
    public MessageResponse deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return MessageResponse.of("Product soft-deleted");
    }

    @PostMapping("/products/{productId}/variants")
    public ResponseEntity<VariantDto> addVariant(@PathVariable Long productId,
                                                 @Valid @RequestBody VariantUpsertRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addVariant(productId, body));
    }

    @PutMapping("/products/{productId}/variants/{variantId}")
    public VariantDto updateVariant(@PathVariable Long productId,
                                    @PathVariable Long variantId,
                                    @Valid @RequestBody VariantUpsertRequest body) {
        return productService.updateVariant(productId, variantId, body);
    }

    @DeleteMapping("/products/{productId}/variants/{variantId}")
    public MessageResponse deleteVariant(@PathVariable Long productId, @PathVariable Long variantId) {
        productService.deleteVariant(productId, variantId);
        return MessageResponse.of("Variant soft-deleted");
    }

    @PostMapping(value = "/products/{productId}/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageDto> uploadImage(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String altText,
            @RequestParam(required = false) Long variantId,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(defaultValue = "false") boolean primary
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.uploadProductImage(productId, file, altText, variantId, displayOrder, primary));
    }

    @PostMapping("/products/{productId}/images")
    public ResponseEntity<ImageDto> addImageByUrl(@PathVariable Long productId,
                                                  @Valid @RequestBody ImageUrlRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addProductImageByUrl(productId, body));
    }

    @PatchMapping("/products/{productId}/images/{imageId}/primary")
    public ImageDto setPrimaryImage(@PathVariable Long productId, @PathVariable Long imageId) {
        return productService.setPrimaryImage(productId, imageId);
    }

    @DeleteMapping("/products/{productId}/images/{imageId}")
    public MessageResponse deleteImage(@PathVariable Long productId, @PathVariable Long imageId) {
        productService.deleteProductImage(productId, imageId);
        return MessageResponse.of("Image deleted");
    }

    @GetMapping("/brands")
    public List<BrandDto> listBrands() {
        return brandService.listAllAdmin();
    }

    @PostMapping("/brands")
    public ResponseEntity<BrandDto> createBrand(@Valid @RequestBody BrandRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(brandService.create(body));
    }

    @PutMapping("/brands/{id}")
    public BrandDto updateBrand(@PathVariable Long id, @Valid @RequestBody BrandRequest body) {
        return brandService.update(id, body);
    }

    @DeleteMapping("/brands/{id}")
    public MessageResponse deleteBrand(@PathVariable Long id) {
        brandService.delete(id);
        return MessageResponse.of("Brand deleted");
    }

    @GetMapping("/categories")
    public List<CategoryDto> listCategories() {
        return categoryService.listAllAdmin();
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(body));
    }

    @PutMapping("/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest body) {
        return categoryService.update(id, body);
    }

    @DeleteMapping("/categories/{id}")
    public MessageResponse deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return MessageResponse.of("Category deleted");
    }

    @GetMapping("/inventory")
    public PageResponse<InventoryDto> listInventory(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean lowOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return inventoryService.searchInventory(search, lowOnly,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @PostMapping("/inventory/adjust")
    public InventoryDto adjustInventory(@Valid @RequestBody InventoryAdjustmentRequest body) {
        return inventoryService.adjustStock(body);
    }

    @GetMapping("/inventory/transactions")
    public PageResponse<InventoryTransactionDto> listInventoryTransactions(
            @RequestParam(required = false) Long variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return inventoryService.listTransactions(variantId,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @GetMapping("/orders")
    public PageResponse<OrderSummaryDto> listOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return orderService.searchAdminOrders(search, status, paymentStatus,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                        Sort.by(Sort.Direction.DESC, "placedAt")));
    }

    @GetMapping("/orders/{id}")
    public OrderDetailDto getOrder(@PathVariable Long id) {
        return orderService.getAdminOrder(id);
    }

    @PutMapping("/orders/{id}/status")
    public OrderDetailDto updateOrderStatus(@PathVariable Long id,
                                            @Valid @RequestBody UpdateOrderStatusRequest body) {
        return orderService.updateStatusByAdmin(id, body.status(), body.note());
    }

    @GetMapping("/payments")
    public PageResponse<PaymentDto> listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return paymentService.listAllAdmin(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @GetMapping("/refunds")
    public PageResponse<RefundDto> listRefunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return returnService.listAllRefundsAdmin(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @PostMapping("/refunds")
    public ResponseEntity<RefundDto> createRefund(@Valid @RequestBody InitiateRefundRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(returnService.initiateRefund(body));
    }

    @GetMapping("/coupons")
    public List<CouponDto> listCoupons() {
        return couponService.listAllAdmin();
    }

    @PostMapping("/coupons")
    public ResponseEntity<CouponDto> createCoupon(@Valid @RequestBody CouponRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.create(body));
    }

    @PutMapping("/coupons/{id}")
    public CouponDto updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponRequest body) {
        return couponService.update(id, body);
    }

    @PatchMapping("/coupons/{id}/active")
    public CouponDto setCouponActive(@PathVariable Long id, @RequestParam boolean active) {
        return couponService.setActive(id, active);
    }

    @GetMapping("/shipments")
    public PageResponse<ShipmentDto> listShipments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return shipmentService.listAllAdmin(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @PostMapping("/shipments")
    public ShipmentDto upsertShipment(@Valid @RequestBody UpsertShipmentRequest body) {
        return shipmentService.createOrUpdateShipment(body);
    }

    @GetMapping("/reviews")
    public PageResponse<ReviewDto> listReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return reviewService.listAdmin(status, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @PatchMapping("/reviews/{id}")
    public ReviewDto moderateReview(@PathVariable Long id, @Valid @RequestBody ModerateReviewRequest body) {
        return reviewService.moderateReview(id, body);
    }

    @DeleteMapping("/reviews/{id}")
    public MessageResponse deleteReview(@PathVariable Long id) {
        reviewService.deleteByAdmin(id);
        return MessageResponse.of("Review deleted");
    }

    @GetMapping("/returns")
    public PageResponse<ReturnRequestDto> listReturns(
            @RequestParam(required = false) ReturnRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return returnService.listAllReturnsAdmin(status,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @PatchMapping("/returns/{id}")
    public ReturnRequestDto decideReturn(@PathVariable Long id, @Valid @RequestBody AdminDecisionRequest body) {
        return returnService.reviewReturnRequest(id, body);
    }

    @GetMapping("/customers")
    public PageResponse<ProfileDto> listCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return customerService.searchCustomers(search, status,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PatchMapping("/customers/{id}/status")
    public ProfileDto setCustomerStatus(@PathVariable Long id, @RequestParam AccountStatus status) {
        return customerService.updateAccountStatus(id, status);
    }

    @GetMapping("/support")
    public PageResponse<SupportTicketDto> listTickets(
            @RequestParam(required = false) SupportTicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return storeConfigService.listTickets(status,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @PatchMapping("/support/{id}")
    public SupportTicketDto replyTicket(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        String reply = body.get("reply");
        SupportTicketStatus status = body.get("status") != null
                ? SupportTicketStatus.valueOf(body.get("status"))
                : SupportTicketStatus.RESOLVED;
        return storeConfigService.replyToTicket(id, reply, status);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLog> listAuditLogs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return PageResponse.of(auditLogService.search(search, action, entityType,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)))));
    }

    @GetMapping("/settings")
    public List<StoreSetting> listSettings() {
        return storeConfigService.listAllSettings();
    }

    @PutMapping("/settings")
    public MessageResponse updateSettings(@RequestBody Map<String, String> updates) {
        storeConfigService.updateSettings(updates);
        return MessageResponse.of("Store settings saved");
    }
}
