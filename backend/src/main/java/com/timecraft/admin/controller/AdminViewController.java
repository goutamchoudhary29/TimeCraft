package com.timecraft.admin.controller;

import com.timecraft.admin.entity.SupportTicketStatus;
import com.timecraft.admin.service.StoreConfigService;
import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.entity.AccountStatus;
import com.timecraft.brand.service.BrandService;
import com.timecraft.brand.service.BrandService.BrandRequest;
import com.timecraft.category.service.CategoryService;
import com.timecraft.category.service.CategoryService.CategoryRequest;
import com.timecraft.coupon.entity.DiscountType;
import com.timecraft.coupon.service.CouponService;
import com.timecraft.coupon.service.CouponService.CouponRequest;
import com.timecraft.customer.service.CustomerService;
import com.timecraft.inventory.entity.InventoryTransactionType;
import com.timecraft.inventory.service.InventoryService;
import com.timecraft.inventory.service.InventoryService.InventoryAdjustmentRequest;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.PaymentStatus;
import com.timecraft.order.service.OrderService;
import com.timecraft.payment.service.RazorpayPaymentService;
import com.timecraft.product.dto.ProductDtos.ImageUrlRequest;
import com.timecraft.product.dto.ProductDtos.ProductUpsertRequest;
import com.timecraft.product.dto.ProductDtos.VariantUpsertRequest;
import com.timecraft.product.entity.MovementType;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.entity.WatchGender;
import com.timecraft.product.service.ProductService;
import com.timecraft.report.service.ReportService;
import com.timecraft.returns.entity.ReturnRequestStatus;
import com.timecraft.returns.service.ReturnAndRefundService;
import com.timecraft.returns.service.ReturnAndRefundService.AdminDecisionRequest;
import com.timecraft.returns.service.ReturnAndRefundService.InitiateRefundRequest;
import com.timecraft.review.entity.ReviewStatus;
import com.timecraft.review.service.ReviewService;
import com.timecraft.review.service.ReviewService.ModerateReviewRequest;
import com.timecraft.shipment.entity.ShipmentStatus;
import com.timecraft.shipment.service.ShipmentService;
import com.timecraft.shipment.service.ShipmentService.UpsertShipmentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Thymeleaf Admin Dashboard MVC controller.
 * Protected by the session-based ADMIN SecurityFilterChain with CSRF tokens on all POST forms.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminViewController {

    private final ReportService reportService;
    private final ProductService productService;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final com.timecraft.order.service.InvoicePdfService invoicePdfService;
    private final RazorpayPaymentService paymentService;
    private final CouponService couponService;
    private final ShipmentService shipmentService;
    private final ReviewService reviewService;
    private final ReturnAndRefundService returnService;
    private final CustomerService customerService;
    private final StoreConfigService storeConfigService;
    private final AuditLogService auditLogService;

    @GetMapping("/login")
    public String loginPage() {
        return "admin/login";
    }

    @GetMapping("/access-denied")
    public String accessDeniedPage() {
        return "admin/access-denied";
    }

    @GetMapping({"", "/"})
    public String adminRoot() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("overview", reportService.getDashboardOverview());
        return "admin/dashboard";
    }

    @GetMapping("/orders/{id}/invoice")
    public org.springframework.http.ResponseEntity<byte[]> downloadAdminOrderInvoice(@PathVariable Long id) {
        var order = orderService.getAdminOrderEntity(id);
        byte[] pdf = invoicePdfService.generateInvoice(order);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"TimeCraft-Invoice-" + order.getOrderNumber() + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/orders/{id}/mark-cod-paid")
    public String markCodOrderPaid(@PathVariable Long id,
                                   @RequestParam(required = false) String note,
                                   RedirectAttributes ra) {
        try {
            paymentService.recordCodPaymentCollected(id, note);
            ra.addFlashAttribute("successMessage", "Cash on Delivery payment recorded as PAID.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    @GetMapping("/customers")
    public String customers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        model.addAttribute("activeNav", "customers");
        model.addAttribute("search", search);
        model.addAttribute("status", status);
        model.addAttribute("customers", customerService.searchCustomers(search, status,
                PageRequest.of(Math.max(0, page), 20, Sort.by(Sort.Direction.DESC, "createdAt"))));
        return "admin/customers";
    }

    @PostMapping("/customers/{id}/status")
    public String updateCustomerStatus(@PathVariable Long id,
                                       @RequestParam AccountStatus status,
                                       RedirectAttributes ra) {
        try {
            customerService.updateAccountStatus(id, status);
            ra.addFlashAttribute("successMessage", "Customer status updated to " + status);
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/customers";
    }

    @GetMapping("/brands")
    public String brands(Model model) {
        model.addAttribute("activeNav", "brands");
        model.addAttribute("brands", brandService.listAllAdmin());
        return "admin/brands";
    }

    @PostMapping("/brands")
    public String saveBrand(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String countryOfOrigin,
            @RequestParam(required = false) Integer foundedYear,
            @RequestParam(required = false) String logoUrl,
            @RequestParam(required = false) String bannerUrl,
            @RequestParam(defaultValue = "false") boolean featured,
            @RequestParam(defaultValue = "true") boolean active,
            RedirectAttributes ra
    ) {
        try {
            BrandRequest req = new BrandRequest(name, slug, description, countryOfOrigin,
                    foundedYear, logoUrl, bannerUrl, featured, active);
            if (id != null) {
                brandService.update(id, req);
                ra.addFlashAttribute("successMessage", "Brand updated");
            } else {
                brandService.create(req);
                ra.addFlashAttribute("successMessage", "Brand created");
            }
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/brands";
    }

    @PostMapping("/brands/{id}/delete")
    public String deleteBrand(@PathVariable Long id, RedirectAttributes ra) {
        try {
            brandService.delete(id);
            ra.addFlashAttribute("successMessage", "Brand removed");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/brands";
    }

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("activeNav", "categories");
        model.addAttribute("categories", categoryService.listAllAdmin());
        return "admin/categories";
    }

    @PostMapping("/categories")
    public String saveCategory(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long parentId,
            @RequestParam String name,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(defaultValue = "true") boolean active,
            RedirectAttributes ra
    ) {
        try {
            CategoryRequest req = new CategoryRequest(parentId, name, slug, description, imageUrl, displayOrder, active);
            if (id != null) {
                categoryService.update(id, req);
                ra.addFlashAttribute("successMessage", "Category updated");
            } else {
                categoryService.create(req);
                ra.addFlashAttribute("successMessage", "Category created");
            }
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes ra) {
        try {
            categoryService.delete(id);
            ra.addFlashAttribute("successMessage", "Category removed");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @GetMapping("/products")
    public String products(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        model.addAttribute("activeNav", "products");
        model.addAttribute("search", search);
        model.addAttribute("status", status);
        model.addAttribute("brandId", brandId);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("brands", brandService.listAllAdmin());
        model.addAttribute("categories", categoryService.listAllAdmin());
        model.addAttribute("statuses", ProductStatus.values());
        model.addAttribute("products", productService.searchAdmin(search, status, brandId, categoryId,
                PageRequest.of(Math.max(0, page), 15, Sort.by(Sort.Direction.DESC, "createdAt"))));
        return "admin/products";
    }

    @GetMapping("/products/new")
    public String newProductForm(Model model) {
        model.addAttribute("activeNav", "products");
        model.addAttribute("brands", brandService.listAllAdmin());
        model.addAttribute("categories", categoryService.listAllAdmin());
        model.addAttribute("movements", MovementType.values());
        model.addAttribute("genders", WatchGender.values());
        model.addAttribute("statuses", ProductStatus.values());
        model.addAttribute("product", null);
        return "admin/product-form";
    }

    @GetMapping("/products/{id}/edit")
    public String editProductForm(@PathVariable Long id, Model model) {
        model.addAttribute("activeNav", "products");
        model.addAttribute("brands", brandService.listAllAdmin());
        model.addAttribute("categories", categoryService.listAllAdmin());
        model.addAttribute("movements", MovementType.values());
        model.addAttribute("genders", WatchGender.values());
        model.addAttribute("statuses", ProductStatus.values());
        model.addAttribute("product", productService.getAdminDetail(id));
        return "admin/product-form";
    }

    @PostMapping("/products/save")
    public String saveProduct(
            @RequestParam(required = false) Long id,
            @RequestParam Long brandId,
            @RequestParam Long categoryId,
            @RequestParam String name,
            @RequestParam(required = false) String slug,
            @RequestParam String modelName,
            @RequestParam String sku,
            @RequestParam(required = false) String shortDescription,
            @RequestParam(required = false) String description,
            @RequestParam MovementType movementType,
            @RequestParam BigDecimal caseDiameterMm,
            @RequestParam String caseMaterial,
            @RequestParam(defaultValue = "50") int waterResistanceM,
            @RequestParam(defaultValue = "Sapphire") String crystalType,
            @RequestParam String strapMaterial,
            @RequestParam String dialColor,
            @RequestParam(required = false) String caseColor,
            @RequestParam(defaultValue = "UNISEX") WatchGender gender,
            @RequestParam(defaultValue = "24") int warrantyMonths,
            @RequestParam BigDecimal regularPrice,
            @RequestParam BigDecimal sellingPrice,
            @RequestParam(defaultValue = "ACTIVE") ProductStatus status,
            @RequestParam(defaultValue = "false") boolean featured,
            @RequestParam(defaultValue = "false") boolean newArrival,
            @RequestParam(defaultValue = "false") boolean bestSeller,
            @RequestParam(required = false) String primaryImageUrl,
            @RequestParam(required = false) Integer initialStock,
            RedirectAttributes ra
    ) {
        try {
            ProductUpsertRequest req = new ProductUpsertRequest(
                    brandId, categoryId, name, slug, modelName, sku, shortDescription, description,
                    movementType, caseDiameterMm, caseMaterial, waterResistanceM, crystalType,
                    strapMaterial, dialColor, caseColor, gender, warrantyMonths, regularPrice,
                    sellingPrice, status, featured, newArrival, bestSeller, primaryImageUrl,
                    initialStock, List.of()
            );
            if (id != null) {
                productService.updateProduct(id, req);
                ra.addFlashAttribute("successMessage", "Timepiece updated successfully");
                return "redirect:/admin/products/" + id + "/edit";
            } else {
                var created = productService.createProduct(req);
                ra.addFlashAttribute("successMessage", "Timepiece created successfully");
                return "redirect:/admin/products/" + created.id() + "/edit";
            }
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return id != null ? "redirect:/admin/products/" + id + "/edit" : "redirect:/admin/products/new";
        }
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes ra) {
        try {
            productService.deleteProduct(id);
            ra.addFlashAttribute("successMessage", "Timepiece archived");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    @GetMapping("/products/{id}/variants")
    public String productVariants(@PathVariable Long id, Model model) {
        model.addAttribute("activeNav", "products");
        model.addAttribute("product", productService.getAdminDetail(id));
        return "admin/product-variants";
    }

    @PostMapping("/products/{id}/variants")
    public String addVariant(
            @PathVariable Long id,
            @RequestParam String sku,
            @RequestParam String name,
            @RequestParam(required = false) String dialColor,
            @RequestParam(required = false) String strapMaterial,
            @RequestParam(required = false) String strapColor,
            @RequestParam(required = false) BigDecimal regularPriceOverride,
            @RequestParam(required = false) BigDecimal sellingPriceOverride,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(defaultValue = "10") Integer initialStock,
            @RequestParam(defaultValue = "5") Integer lowStockThreshold,
            RedirectAttributes ra
    ) {
        try {
            productService.addVariant(id, new VariantUpsertRequest(
                    sku, name, dialColor, strapMaterial, strapColor, regularPriceOverride,
                    sellingPriceOverride, displayOrder, active, initialStock, lowStockThreshold));
            ra.addFlashAttribute("successMessage", "Variant added");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products/" + id + "/variants";
    }

    @PostMapping("/products/{productId}/variants/{variantId}/delete")
    public String deleteVariant(@PathVariable Long productId, @PathVariable Long variantId, RedirectAttributes ra) {
        try {
            productService.deleteVariant(productId, variantId);
            ra.addFlashAttribute("successMessage", "Variant archived");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/variants";
    }

    @GetMapping("/products/{id}/images")
    public String productImages(@PathVariable Long id, Model model) {
        model.addAttribute("activeNav", "products");
        model.addAttribute("product", productService.getAdminDetail(id));
        return "admin/product-images";
    }

    @PostMapping("/products/{id}/images/upload")
    public String uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String altText,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(defaultValue = "false") boolean primary,
            RedirectAttributes ra
    ) {
        try {
            productService.uploadProductImage(id, file, altText, null, displayOrder, primary);
            ra.addFlashAttribute("successMessage", "Image uploaded to Cloudinary");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products/" + id + "/images";
    }

    @PostMapping("/products/{id}/images/url")
    public String addImageUrl(
            @PathVariable Long id,
            @RequestParam String url,
            @RequestParam(required = false) String altText,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(defaultValue = "false") boolean primary,
            RedirectAttributes ra
    ) {
        try {
            productService.addProductImageByUrl(id, new ImageUrlRequest(url, altText, null, displayOrder, primary));
            ra.addFlashAttribute("successMessage", "Image added");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products/" + id + "/images";
    }

    @PostMapping("/products/{productId}/images/{imageId}/primary")
    public String setPrimaryImage(@PathVariable Long productId, @PathVariable Long imageId, RedirectAttributes ra) {
        try {
            productService.setPrimaryImage(productId, imageId);
            ra.addFlashAttribute("successMessage", "Primary image updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/images";
    }

    @PostMapping("/products/{productId}/images/{imageId}/delete")
    public String deleteImage(@PathVariable Long productId, @PathVariable Long imageId, RedirectAttributes ra) {
        try {
            productService.deleteProductImage(productId, imageId);
            ra.addFlashAttribute("successMessage", "Image deleted");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/images";
    }

    @GetMapping("/inventory")
    public String inventory(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean lowOnly,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        model.addAttribute("activeNav", "inventory");
        model.addAttribute("search", search);
        model.addAttribute("lowOnly", lowOnly);
        model.addAttribute("inventory", inventoryService.searchInventory(search, lowOnly,
                PageRequest.of(Math.max(0, page), 20)));
        model.addAttribute("transactions", inventoryService.listTransactions(null, PageRequest.of(0, 15)));
        return "admin/inventory";
    }

    @PostMapping("/inventory/adjust")
    public String adjustInventory(
            @RequestParam Long variantId,
            @RequestParam InventoryTransactionType type,
            @RequestParam int quantity,
            @RequestParam(required = false) Integer lowStockThreshold,
            @RequestParam(required = false) String note,
            RedirectAttributes ra
    ) {
        try {
            inventoryService.adjustStock(new InventoryAdjustmentRequest(variantId, type, quantity, lowStockThreshold, note));
            ra.addFlashAttribute("successMessage", "Inventory updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/inventory";
    }

    @GetMapping("/orders")
    public String orders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        model.addAttribute("activeNav", "orders");
        model.addAttribute("search", search);
        model.addAttribute("status", status);
        model.addAttribute("paymentStatus", paymentStatus);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("paymentStatuses", PaymentStatus.values());
        model.addAttribute("orders", orderService.searchAdminOrders(search, status, paymentStatus,
                PageRequest.of(Math.max(0, page), 20, Sort.by(Sort.Direction.DESC, "placedAt"))));
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetails(@PathVariable Long id, Model model) {
        model.addAttribute("activeNav", "orders");
        model.addAttribute("order", orderService.getAdminOrder(id));
        model.addAttribute("payments", paymentService.listForOrder(id));
        model.addAttribute("shipment", shipmentService.getForOrder(id));
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("shipmentStatuses", ShipmentStatus.values());
        return "admin/order-details";
    }

    @PostMapping("/orders/{id}/status")
    public String updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status,
            @RequestParam(required = false) String note,
            RedirectAttributes ra
    ) {
        try {
            orderService.updateStatusByAdmin(id, status, note);
            ra.addFlashAttribute("successMessage", "Order status updated to " + status);
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    @GetMapping("/payments")
    public String payments(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("activeNav", "payments");
        model.addAttribute("payments", paymentService.listAllAdmin(PageRequest.of(Math.max(0, page), 20)));
        return "admin/payments";
    }

    @GetMapping("/refunds")
    public String refunds(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("activeNav", "refunds");
        model.addAttribute("refunds", returnService.listAllRefundsAdmin(PageRequest.of(Math.max(0, page), 20)));
        return "admin/refunds";
    }

    @PostMapping("/refunds")
    public String createRefund(
            @RequestParam Long orderId,
            @RequestParam(required = false) Long returnRequestId,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam String reason,
            RedirectAttributes ra
    ) {
        try {
            returnService.initiateRefund(new InitiateRefundRequest(orderId, returnRequestId, amount, reason));
            ra.addFlashAttribute("successMessage", "Refund processed");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/refunds";
    }

    @GetMapping("/coupons")
    public String coupons(Model model) {
        model.addAttribute("activeNav", "coupons");
        model.addAttribute("coupons", couponService.listAllAdmin());
        model.addAttribute("discountTypes", DiscountType.values());
        return "admin/coupons";
    }

    @PostMapping("/coupons")
    public String saveCoupon(
            @RequestParam(required = false) Long id,
            @RequestParam String code,
            @RequestParam(required = false) String description,
            @RequestParam DiscountType discountType,
            @RequestParam BigDecimal discountValue,
            @RequestParam(defaultValue = "0") BigDecimal minOrderValue,
            @RequestParam(required = false) BigDecimal maxDiscount,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer usageLimit,
            @RequestParam(required = false) Integer perCustomerLimit,
            @RequestParam(defaultValue = "true") boolean active,
            RedirectAttributes ra
    ) {
        try {
            Instant startsAt = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant expiresAt = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusSeconds(1);
            CouponRequest req = new CouponRequest(code, description, discountType, discountValue,
                    minOrderValue, maxDiscount, startsAt, expiresAt, usageLimit, perCustomerLimit, active);
            if (id != null) {
                couponService.update(id, req);
                ra.addFlashAttribute("successMessage", "Coupon updated");
            } else {
                couponService.create(req);
                ra.addFlashAttribute("successMessage", "Coupon created");
            }
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/coupons";
    }

    @PostMapping("/coupons/{id}/toggle")
    public String toggleCoupon(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes ra) {
        try {
            couponService.setActive(id, active);
            ra.addFlashAttribute("successMessage", "Coupon status updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/coupons";
    }

    @GetMapping("/shipments")
    public String shipments(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("activeNav", "shipments");
        model.addAttribute("shipments", shipmentService.listAllAdmin(PageRequest.of(Math.max(0, page), 20)));
        model.addAttribute("shipmentStatuses", ShipmentStatus.values());
        return "admin/shipments";
    }

    @PostMapping("/shipments")
    public String saveShipment(
            @RequestParam Long orderId,
            @RequestParam String courierName,
            @RequestParam String trackingNumber,
            @RequestParam(required = false) String trackingUrl,
            @RequestParam ShipmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate estimatedDeliveryDate,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String eventDescription,
            RedirectAttributes ra
    ) {
        try {
            shipmentService.createOrUpdateShipment(new UpsertShipmentRequest(
                    orderId, courierName, trackingNumber, trackingUrl, status,
                    estimatedDeliveryDate, location, eventDescription));
            ra.addFlashAttribute("successMessage", "Shipment updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/shipments";
    }

    @GetMapping("/reviews")
    public String reviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        model.addAttribute("activeNav", "reviews");
        model.addAttribute("status", status);
        model.addAttribute("reviews", reviewService.listAdmin(status, PageRequest.of(Math.max(0, page), 20)));
        return "admin/reviews";
    }

    @PostMapping("/reviews/{id}/moderate")
    public String moderateReview(
            @PathVariable Long id,
            @RequestParam ReviewStatus status,
            @RequestParam(required = false) String moderationNote,
            RedirectAttributes ra
    ) {
        try {
            reviewService.moderateReview(id, new ModerateReviewRequest(status, moderationNote));
            ra.addFlashAttribute("successMessage", "Review moderated to " + status);
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/reviews";
    }

    @PostMapping("/reviews/{id}/delete")
    public String deleteReview(@PathVariable Long id, RedirectAttributes ra) {
        try {
            reviewService.deleteByAdmin(id);
            ra.addFlashAttribute("successMessage", "Review deleted");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/reviews";
    }

    @GetMapping("/returns")
    public String returns(
            @RequestParam(required = false) ReturnRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        model.addAttribute("activeNav", "returns");
        model.addAttribute("status", status);
        model.addAttribute("returns", returnService.listAllReturnsAdmin(status, PageRequest.of(Math.max(0, page), 20)));
        return "admin/returns";
    }

    @PostMapping("/returns/{id}/decision")
    public String decideReturn(
            @PathVariable Long id,
            @RequestParam ReturnRequestStatus status,
            @RequestParam(required = false) String adminNote,
            RedirectAttributes ra
    ) {
        try {
            returnService.reviewReturnRequest(id, new AdminDecisionRequest(status, adminNote));
            ra.addFlashAttribute("successMessage", "Return request updated to " + status);
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/returns";
    }

    @GetMapping("/support")
    public String support(
            @RequestParam(required = false) SupportTicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        model.addAttribute("activeNav", "support");
        model.addAttribute("status", status);
        model.addAttribute("tickets", storeConfigService.listTickets(status, PageRequest.of(Math.max(0, page), 20)));
        return "admin/support";
    }

    @PostMapping("/support/{id}/reply")
    public String replySupport(
            @PathVariable Long id,
            @RequestParam String reply,
            @RequestParam(defaultValue = "RESOLVED") SupportTicketStatus status,
            RedirectAttributes ra
    ) {
        try {
            storeConfigService.replyToTicket(id, reply, status);
            ra.addFlashAttribute("successMessage", "Support ticket updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/support";
    }

    @GetMapping("/reports")
    public String reports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model
    ) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(29);
        model.addAttribute("activeNav", "reports");
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("report", reportService.generateSalesReport(start, end));
        return "admin/reports";
    }

    @GetMapping("/audit-logs")
    public String auditLogs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        model.addAttribute("activeNav", "audit");
        model.addAttribute("search", search);
        model.addAttribute("action", action);
        model.addAttribute("entityType", entityType);
        model.addAttribute("logs", auditLogService.search(search, action, entityType,
                PageRequest.of(Math.max(0, page), 30)));
        return "admin/audit-logs";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("activeNav", "settings");
        model.addAttribute("settings", storeConfigService.listAllSettings());
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String updateSettings(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        params.remove("_csrf");
        storeConfigService.updateSettings(params);
        ra.addFlashAttribute("successMessage", "Store configuration updated");
        return "redirect:/admin/settings";
    }
}
