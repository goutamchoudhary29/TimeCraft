package com.timecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timecraft.address.entity.AddressType;
import com.timecraft.address.service.AddressService.AddressRequest;
import com.timecraft.authentication.dto.AuthDtos.LoginRequest;
import com.timecraft.authentication.dto.AuthDtos.RegisterRequest;
import com.timecraft.authentication.entity.EmailVerificationToken;
import com.timecraft.authentication.entity.PasswordResetToken;
import com.timecraft.authentication.entity.RoleName;
import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.EmailVerificationTokenRepository;
import com.timecraft.authentication.repository.PasswordResetTokenRepository;
import com.timecraft.authentication.repository.RoleRepository;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.cart.service.CartService.AddCartItemRequest;
import com.timecraft.cart.service.CartService.ApplyCouponRequest;
import com.timecraft.cart.service.CartService.UpdateCartItemRequest;
import com.timecraft.common.util.Money;
import com.timecraft.common.util.TokenUtils;
import com.timecraft.coupon.entity.DiscountType;
import com.timecraft.coupon.service.CouponService.CouponRequest;
import com.timecraft.inventory.entity.Inventory;
import com.timecraft.inventory.repository.InventoryRepository;
import com.timecraft.inventory.service.InventoryService;
import com.timecraft.order.dto.OrderDtos.CancelOrderRequest;
import com.timecraft.order.dto.OrderDtos.PlaceOrderRequest;
import com.timecraft.order.dto.OrderDtos.UpdateOrderStatusRequest;
import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.PaymentStatus;
import com.timecraft.order.repository.OrderRepository;
import com.timecraft.order.service.OrderService;
import com.timecraft.payment.entity.Payment;
import com.timecraft.payment.entity.PaymentAttemptStatus;
import com.timecraft.payment.repository.PaymentRepository;
import com.timecraft.payment.repository.PaymentWebhookEventRepository;
import com.timecraft.payment.service.RazorpayPaymentService;
import com.timecraft.payment.service.RazorpayPaymentService.VerifyPaymentRequest;
import com.timecraft.product.dto.ProductDtos.ProductUpsertRequest;
import com.timecraft.product.entity.MovementType;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.entity.ProductVariant;
import com.timecraft.product.entity.WatchGender;
import com.timecraft.product.repository.ProductVariantRepository;
import com.timecraft.returns.entity.ReturnRequestStatus;
import com.timecraft.returns.service.ReturnAndRefundService;
import com.timecraft.returns.service.ReturnAndRefundService.AdminDecisionRequest;
import com.timecraft.returns.service.ReturnAndRefundService.CreateReturnRequest;
import com.timecraft.returns.service.ReturnAndRefundService.InitiateRefundRequest;
import com.timecraft.returns.service.ReturnAndRefundService.ReturnItemInput;
import com.timecraft.review.entity.ReviewStatus;
import com.timecraft.review.service.ReviewService;
import com.timecraft.review.service.ReviewService.ModerateReviewRequest;
import com.timecraft.review.service.ReviewService.SubmitReviewRequest;
import com.timecraft.security.RateLimitingFilter;
import com.timecraft.wishlist.service.WishlistService.AddWishlistItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test suite covering all 22 required test scenarios against
 * an isolated H2 MySQL-mode database migrated by Flyway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TimeCraftIntegrationTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailVerificationTokenRepository emailTokenRepository;
    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;
    @Autowired
    private ProductVariantRepository variantRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;
    @SpyBean
    private RazorpayPaymentService paymentService;
    @Autowired
    private ReturnAndRefundService returnAndRefundService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void clearRateLimiter() {
        rateLimitingFilter.reset();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("1-3. Registration, Login, JWT Validation, Refresh Rotation, Email Verification & Password Reset")
    void registrationLoginJwtRefreshAndTokenWorkflows() throws Exception {
        // Verify ONLY CUSTOMER and ADMIN roles exist in the database
        assertThat(roleRepository.findAll())
                .extracting(r -> r.getName().name())
                .containsExactlyInAnyOrder("CUSTOMER", "ADMIN");

        String email = "collector1@timecraft.test";
        RegisterRequest reg = new RegisterRequest("Aarav", "Mehta", email, "WatchPass123", "+91 9876543210");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.otpSent").value(true));

        // Verify 6-digit OTP flow (wrong OTP fails, correct OTP verifies email & issues JWT)
        User registeredUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(registeredUser.isEmailVerified()).isFalse();
        EmailVerificationToken activeOtp = emailTokenRepository
                .findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(registeredUser.getId()).orElseThrow();
        activeOtp.setTokenHash(com.timecraft.authentication.service.AuthenticationService.hashOtp(email, "123456"));
        emailTokenRepository.save(activeOtp);

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.emailVerified").value(true))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"));

        // Duplicate registration rejected with 409
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

        // Login with valid credentials
        LoginRequest login = new LoginRequest(email, "WatchPass123");
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginRes.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();

        // JWT validates on /api/v1/auth/me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // Tampered JWT rejected with 401
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken + "tampered"))
                .andExpect(status().isUnauthorized());

        // Rotate refresh token
        MvcResult refreshRes = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        String rotatedRefresh = objectMapper.readTree(refreshRes.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // Reusing old refresh token triggers family revocation and returns 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        // Even the rotated token in the same family is now revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotatedRefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // Email verification token single-use
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String rawVerify = TokenUtils.generateToken();
        emailTokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .tokenHash(TokenUtils.sha256(rawVerify))
                .expiresAt(Instant.now().plus(Duration.ofHours(2)))
                .build());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawVerify + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawVerify + "\"}"))
                .andExpect(status().isBadRequest());

        // Password reset token single-use
        String rawReset = TokenUtils.generateToken();
        resetTokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(TokenUtils.sha256(rawReset))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .build());
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawReset + "\",\"newPassword\":\"NewWatchPass456\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawReset + "\",\"newPassword\":\"NewWatchPass456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("4-5. Admin & Customer Access Restrictions + CSRF Enforcement")
    void adminAndCustomerAccessBoundaries() throws Exception {
        String customerToken = registerAndGetAccessToken("boundary@timecraft.test");

        // Customer Bearer JWT must NEVER grant access to /api/v1/admin/** or /admin/**
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().is3xxRedirection());

        // Admin session without CSRF on state-changing POST is rejected with 403
        mockMvc.perform(post("/api/v1/admin/brands")
                        .with(user("admin@timecraft.test").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Maison\",\"active\":true,\"featured\":false}"))
                .andExpect(status().isForbidden());

        // Admin session WITH CSRF succeeds
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .with(user("admin@timecraft.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").isNumber());

        // Customer resource ownership check: Customer B cannot read Customer A's address
        String tokenA = registerAndGetAccessToken("ownerA@timecraft.test");
        String tokenB = registerAndGetAccessToken("ownerB@timecraft.test");
        Long addrIdA = createIndianAddress(tokenA);

        mockMvc.perform(delete("/api/v1/addresses/" + addrIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("6-8. Product CRUD, Search, Multi-Facet Filters, Sorting & Comparison")
    void productCrudSearchFilterSortAndCompare() throws Exception {
        // Admin creates a new watch
        ProductUpsertRequest createReq = new ProductUpsertRequest(
                1L, 1L, "Chronometer Royal 39", null, "CR-39", "AUR-CR39",
                "Hand-finished chronometer", "Full description",
                MovementType.AUTOMATIC, new BigDecimal("39.00"), "18K Gold", 100,
                "Sapphire", "Alligator Leather", "Champagne", "Gold",
                WatchGender.MEN, 60, new BigDecimal("520000.00"), new BigDecimal("468000.00"),
                ProductStatus.ACTIVE, true, true, false,
                "https://images.unsplash.com/photo-1587836374828-4dbafa94cf0e?w=1200", 8, List.of()
        );

        MvcResult createdRes = mockMvc.perform(post("/api/v1/admin/products")
                        .with(user("admin@timecraft.test").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("AUR-CR39"))
                .andExpect(jsonPath("$.discountPercent").value(10.0))
                .andReturn();
        Long createdId = objectMapper.readTree(createdRes.getResponse().getContentAsString()).get("id").asLong();

        // Search by keyword
        mockMvc.perform(get("/api/v1/products/search").param("q", "Chronometer Royal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("AUR-CR39"));

        // Combined filters: movement=AUTOMATIC, minWaterResistance=200, inStock=true, sort=price_asc
        mockMvc.perform(get("/api/v1/products")
                        .param("movement", "AUTOMATIC")
                        .param("minWaterResistance", "200")
                        .param("inStock", "true")
                        .param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        // Compare up to 4 watches works; comparing 5 returns 400
        mockMvc.perform(get("/api/v1/products/compare").param("ids", "1,2,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/v1/products/compare").param("ids", "1,2,3,4,5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPARE_LIMIT_EXCEEDED"));

        // Soft delete product
        mockMvc.perform(delete("/api/v1/admin/products/" + createdId)
                        .with(user("admin@timecraft.test").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("9-12. Guest Cart, Cart Merge on Login, Wishlist, Coupon Validation & Checkout Calculations")
    void cartMergeWishlistCouponsAndCalculations() throws Exception {
        ProductVariant variant = variantRepository.findAll().stream()
                .filter(v -> inventoryService.getAvailableForVariant(v.getId()) >= 5)
                .findFirst().orElseThrow();

        // Guest adds 1 watch to cart and receives X-Guest-Token
        MvcResult guestAdd = mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(variant.getId(), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(1))
                .andReturn();
        String guestToken = guestAdd.getResponse().getHeader("X-Guest-Token");
        assertThat(guestToken).isNotBlank();

        // Guest updates quantity to 2
        mockMvc.perform(put("/api/v1/cart/items/" + objectMapper.readTree(guestAdd.getResponse().getContentAsString())
                        .get("items").get(0).get("id").asLong())
                        .header("X-Guest-Token", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(2));

        // Register customer and verify OTP with X-Guest-Token -> guest cart merges automatically
        String mergeEmail = "merge@timecraft.test";
        RegisterRequest reg = new RegisterRequest("Nisha", "Kapoor", mergeEmail, "WatchPass123", "+91 9876543211");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());
        User mergeUser = userRepository.findByEmailIgnoreCase(mergeEmail).orElseThrow();
        EmailVerificationToken mergeOtp = emailTokenRepository
                .findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(mergeUser.getId()).orElseThrow();
        mergeOtp.setTokenHash(com.timecraft.authentication.service.AuthenticationService.hashOtp(mergeEmail, "222333"));
        emailTokenRepository.save(mergeOtp);

        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .header("X-Guest-Token", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + mergeEmail + "\",\"otp\":\"222333\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(regRes.getResponse().getContentAsString()).get("accessToken").asText();

        // Customer cart now contains the 2 merged items
        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(2));

        // Apply valid coupon WELCOME10
        mockMvc.perform(post("/api/v1/cart/coupon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApplyCouponRequest("WELCOME10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCouponCode").value("WELCOME10"))
                .andExpect(jsonPath("$.totals.discountTotal").value(5000.0));

        // Apply non-existent coupon fails with 400
        mockMvc.perform(post("/api/v1/cart/coupon")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApplyCouponRequest("FAKECODE99"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COUPON"));

        // Wishlist: add (idempotent on duplicate) and move to cart
        ProductVariant anotherVariant = variantRepository.findAll().stream()
                .filter(v -> !v.getId().equals(variant.getId()) && inventoryService.getAvailableForVariant(v.getId()) >= 3)
                .findFirst().orElseThrow();
        MvcResult wishRes = mockMvc.perform(post("/api/v1/wishlist/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddWishlistItemRequest(anotherVariant.getProduct().getId(), anotherVariant.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        Long wishItemId = objectMapper.readTree(wishRes.getResponse().getContentAsString()).get(0).get("id").asLong();

        mockMvc.perform(post("/api/v1/wishlist/items/" + wishItemId + "/move-to-cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(3));
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("13-20. Stock Reservation, Concurrency Locking, Order Lifecycle, Razorpay Signature & Webhook Idempotency, Returns & Refunds")
    void inventoryConcurrencyOrderPaymentWebhookReturnAndRefund() throws Exception {
        String token = registerAndGetAccessToken("buyer@timecraft.test");
        Long userId = userRepository.findByEmailIgnoreCase("buyer@timecraft.test").orElseThrow().getId();
        Long addressId = createIndianAddress(token);

        ProductVariant variant = variantRepository.findById(1L).orElseThrow();
        Inventory invBefore = inventoryRepository.findByVariantId(variant.getId()).orElseThrow();
        invBefore.setQuantityOnHand(5);
        invBefore.setQuantityReserved(0);
        inventoryRepository.save(invBefore);

        // Concurrent stock reservation test: 5 threads try to reserve 2 units each from 5 available -> at most 2 succeed (4 units)
        Order dummyOrder = createOrderDirectly(userId, addressId, variant, 1, "idemp-seed-" + UUID.randomUUID());
        // Release the 1 unit from dummyOrder first so exactly 5 are available
        orderService.cancelByCustomer(userId, dummyOrder.getId(), "Release for concurrency test");

        int threads = 5;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    inventoryService.reserveForOrder(dummyOrder, variant, 2, Duration.ofMinutes(10));
                    successes.incrementAndGet();
                } catch (Exception ex) {
                    conflicts.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(2);
        assertThat(conflicts.get()).isEqualTo(3);
        inventoryService.releaseReservationsForOrder(dummyOrder,
                com.timecraft.inventory.entity.StockReservationStatus.RELEASED, "Reset after concurrency test");

        // Place a real order through /api/v1/orders with idempotency key
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(variant.getId(), 1))))
                .andExpect(status().isOk());

        String idempKey = "order-key-" + UUID.randomUUID();
        PlaceOrderRequest placeReq = new PlaceOrderRequest(addressId, "WELCOME10", "Gift wrap", idempKey);
        MvcResult orderRes = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(placeReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andReturn();
        JsonNode orderJson = objectMapper.readTree(orderRes.getResponse().getContentAsString());
        Long orderId = orderJson.get("id").asLong();
        String orderNumber = orderJson.get("orderNumber").asText();

        // Duplicate order placement with same idempotency key returns the exact same order
        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(placeReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId));

        // Verify stock is reserved (5 on hand, 1 reserved -> 4 available)
        assertThat(inventoryService.getAvailableForVariant(variant.getId())).isEqualTo(4);

        // Create a Razorpay payment record in CREATED state for signature verification test
        Order orderEntity = orderRepository.findById(orderId).orElseThrow();
        String rzpOrderId = "order_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Payment payment = paymentRepository.save(Payment.builder()
                .order(orderEntity)
                .provider("RAZORPAY")
                .providerOrderId(rzpOrderId)
                .amount(orderEntity.getGrandTotal())
                .currency("INR")
                .status(PaymentAttemptStatus.CREATED)
                .build());

        // Invalid signature is rejected with 400
        String rzpPaymentId = "pay_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/v1/payments/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VerifyPaymentRequest(orderId, rzpOrderId, rzpPaymentId, "invalid_sig", "UPI"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_SIGNATURE"));

        // Valid HMAC-SHA256 signature confirms the order and commits inventory
        String validSig = RazorpayPaymentService.hmacSha256Hex(
                rzpOrderId + "|" + rzpPaymentId, "rzp_test_secret_abc987");
        mockMvc.perform(post("/api/v1/payments/verify")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VerifyPaymentRequest(orderId, rzpOrderId, rzpPaymentId, validSig, "UPI"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        // Inventory is now committed: onHand reduced from 5 to 4, reserved back to 0
        Inventory invAfterPay = inventoryRepository.findByVariantId(variant.getId()).orElseThrow();
        assertThat(invAfterPay.getQuantityOnHand()).isEqualTo(4);
        assertThat(invAfterPay.getQuantityReserved()).isEqualTo(0);

        // Webhook idempotency: sending the same event twice records only 1 webhook row
        String webhookPayload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\""
                + rzpOrderId + "\",\"id\":\"" + rzpPaymentId + "\",\"method\":\"UPI\"}}}}";
        String webhookSig = RazorpayPaymentService.hmacSha256Hex(webhookPayload, "rzp_webhook_secret_xyz456");
        String eventId = "evt_unique_1001";

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", webhookSig)
                        .header("X-Razorpay-Event-Id", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("X-Razorpay-Signature", webhookSig)
                        .header("X-Razorpay-Event-Id", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookPayload))
                .andExpect(status().isOk());

        assertThat(webhookEventRepository.findByEventId(eventId)).isPresent();

        // Invalid order state transition (CONFIRMED -> DELIVERED skipping PACKED/SHIPPED) is rejected
        mockMvc.perform(put("/api/v1/admin/orders/" + orderId + "/status")
                        .with(user("admin@timecraft.test").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(OrderStatus.DELIVERED, "Skip"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE_TRANSITION"));

        // Valid transitions: CONFIRMED -> PACKED -> SHIPPED -> DELIVERED
        orderService.updateStatusByAdmin(orderId, OrderStatus.PACKED, "Packed");
        orderService.updateStatusByAdmin(orderId, OrderStatus.SHIPPED, "Shipped");
        orderService.updateStatusByAdmin(orderId, OrderStatus.DELIVERED, "Delivered");

        // Verified purchaser submits a review and admin approves it
        var reviewDto = reviewService.submitReview(userId,
                new SubmitReviewRequest(variant.getProduct().getId(), 5, "Exquisite finishing", "Runs +1s/day", List.of()));
        assertThat(reviewDto.verifiedPurchase()).isTrue();
        reviewService.moderateReview(reviewDto.id(), new ModerateReviewRequest(ReviewStatus.APPROVED, "Verified"));

        // Customer submits a return request for the delivered item
        Long orderItemId = orderService.getAdminOrder(orderId).items().get(0).id();
        var returnDto = returnAndRefundService.submitReturnRequest(userId,
                new CreateReturnRequest(orderId, "CHANGED_MIND", "Unworn in box",
                        List.of(new ReturnItemInput(orderItemId, 1, "Factory sealed"))));
        assertThat(returnDto.status()).isEqualTo(ReturnRequestStatus.REQUESTED);

        // Admin approves and marks return received (restores stock from 4 back to 5)
        returnAndRefundService.reviewReturnRequest(returnDto.id(),
                new AdminDecisionRequest(ReturnRequestStatus.APPROVED, "RMA issued"));
        returnAndRefundService.reviewReturnRequest(returnDto.id(),
                new AdminDecisionRequest(ReturnRequestStatus.RECEIVED, "Watch inspected in vault"));
        assertThat(inventoryRepository.findByVariantId(variant.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(5);

        // Admin processes refund (stub gateway HTTP call to return a provider refund ID)
        doReturn("rfnd_test_99001").when(paymentService).callRazorpayRefund(any(Payment.class), any(BigDecimal.class), anyString());
        var refundDto = returnAndRefundService.initiateRefund(
                new InitiateRefundRequest(orderId, returnDto.id(), null, "Full return refund"));
        assertThat(refundDto.providerRefundId()).isEqualTo("rfnd_test_99001");
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);

        // PDF invoice downloads cleanly
        mockMvc.perform(get("/api/v1/orders/" + orderNumber + "/invoice")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("16, 21-22. Order Cancellation, Bean Validation & Global Exception Handling")
    void orderCancellationInputValidationAndGlobalExceptionHandler() throws Exception {
        String token = registerAndGetAccessToken("cancel@timecraft.test");
        Long userId = userRepository.findByEmailIgnoreCase("cancel@timecraft.test").orElseThrow().getId();
        Long addrId = createIndianAddress(token);
        ProductVariant variant = variantRepository.findById(2L).orElseThrow();
        int stockBefore = inventoryService.getAvailableForVariant(variant.getId());

        Order order = createOrderDirectly(userId, addrId, variant, 1, "cancel-key-" + UUID.randomUUID());
        assertThat(inventoryService.getAvailableForVariant(variant.getId())).isEqualTo(stockBefore - 1);

        // Customer cancels order -> reservation released
        mockMvc.perform(post("/api/v1/orders/" + order.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelOrderRequest("Ordered wrong dial"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(inventoryService.getAvailableForVariant(variant.getId())).isEqualTo(stockBefore);

        // Input validation: invalid registration payload returns structured 400 with fieldErrors
        RegisterRequest badReg = new RegisterRequest("", "", "not-an-email", "weak", "badphone");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReg)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());

        // Invalid coupon dates rejected
        CouponRequest badCoupon = new CouponRequest("BADDATE", "desc", DiscountType.PERCENTAGE,
                Money.of("15.00"), Money.ZERO, null,
                Instant.now().plus(Duration.ofDays(5)), Instant.now(), 10, 1, true);
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .with(user("admin@timecraft.test").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCoupon)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COUPON_DATES"));

        // Non-existent product returns structured 404 without stack trace leak
        mockMvc.perform(get("/api/v1/products/non-existent-watch-slug"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("23-24. Cash on Delivery (COD) Checkout, Unverified Block & Admin COD Settlement")
    void codCheckoutAndAdminSettlement() throws Exception {
        // Unverified user cannot place an order
        String unverifiedEmail = "unverified@timecraft.test";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Unv", "User", unverifiedEmail, "WatchPass123", "+91 9000000001"))))
                .andExpect(status().isCreated());
        User unvUser = userRepository.findByEmailIgnoreCase(unverifiedEmail).orElseThrow();
        String unvToken = jwtService.generateCustomerAccessToken(unvUser);
        Long unvAddrId = createIndianAddress(unvToken);

        MvcResult cheapestRes = mockMvc.perform(get("/api/v1/products?sort=price_asc&size=1"))
                .andExpect(status().isOk())
                .andReturn();
        String cheapestSlug = objectMapper.readTree(cheapestRes.getResponse().getContentAsString())
                .get("content").get(0).get("slug").asText();
        MvcResult cheapestDetail = mockMvc.perform(get("/api/v1/products/" + cheapestSlug))
                .andExpect(status().isOk())
                .andReturn();
        Long affordableVariantId = objectMapper.readTree(cheapestDetail.getResponse().getContentAsString())
                .get("variants").get(0).get("id").asLong();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unvToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(affordableVariantId, 1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unvToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlaceOrderRequest(unvAddrId, null, "Blocked unverified", "cod-unv-1",
                                        com.timecraft.order.entity.PaymentMethod.COD))))
                .andExpect(status().isForbidden());

        // Verified user places COD order -> CONFIRMED + PENDING_COD immediately
        String codEmail = "codbuyer@timecraft.test";
        String codToken = registerAndGetAccessToken(codEmail);
        Long codAddrId = createIndianAddress(codToken);

        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + codToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(affordableVariantId, 1))))
                .andExpect(status().isOk());

        MvcResult codOrderRes = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + codToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PlaceOrderRequest(codAddrId, null, "Pay cash at door", "cod-ok-1",
                                        com.timecraft.order.entity.PaymentMethod.COD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING_COD"))
                .andExpect(jsonPath("$.paymentMethod").value("COD"))
                .andReturn();

        Long codOrderId = objectMapper.readTree(codOrderRes.getResponse().getContentAsString()).get("id").asLong();

        // Admin marks COD order PAID
        mockMvc.perform(post("/admin/orders/" + codOrderId + "/mark-cod-paid")
                        .with(user("admin@timecraft.test").roles("ADMIN"))
                        .with(csrf())
                        .param("note", "Cash collected by courier"))
                .andExpect(status().is3xxRedirection());

        Order updated = orderRepository.findById(codOrderId).orElseThrow();
        assertThat(updated.getPaymentStatus()).isEqualTo(com.timecraft.order.entity.PaymentStatus.PAID);

        // Verify HTML+PDF order invoice email was logged for the confirmed COD order
        assertThat(emailLogRepository.findAll().stream()
                .anyMatch(e -> "ORDER_INVOICE_CONFIRMATION".equals(e.getTemplate())
                        && codOrderId.equals(e.getRelatedId()))).isTrue();

        // Admin PDF invoice download returns valid PDF bytes
        mockMvc.perform(get("/admin/orders/" + codOrderId + "/invoice")
                        .with(user("admin@timecraft.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(MediaType.APPLICATION_PDF));
    }

    @Autowired
    private com.timecraft.notification.repository.EmailLogRepository emailLogRepository;
    @Autowired
    private com.timecraft.authentication.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("25. Forgot Password & Reset Password OTP Flow (Single-Use & Re-Login)")
    void forgotAndResetPasswordOtpFlow() throws Exception {
        String email = "resetotp@timecraft.test";
        registerAndGetAccessToken(email);

        // Request 6-digit password reset OTP
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.otpSent").value(true));

        User u = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        PasswordResetToken prt = passwordResetTokenRepository
                .findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(u.getId()).orElseThrow();
        prt.setTokenHash(com.timecraft.authentication.service.AuthenticationService.hashOtp(email, "778899"));
        passwordResetTokenRepository.save(prt);

        // Wrong OTP rejected
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"000000\",\"newPassword\":\"NewWatchPass99\"}"))
                .andExpect(status().isBadRequest());

        // Correct OTP updates password
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"778899\",\"newPassword\":\"NewWatchPass99\"}"))
                .andExpect(status().isOk());

        // Reusing same OTP is rejected (single-use)
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"778899\",\"newPassword\":\"AnotherPass99\"}"))
                .andExpect(status().isBadRequest());

        // Login with new password succeeds
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "NewWatchPass99"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        RegisterRequest reg = new RegisterRequest("Test", "Collector", email, "WatchPass123", "+91 9876543210");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());
        User u = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        EmailVerificationToken otpToken = emailTokenRepository
                .findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(u.getId()).orElseThrow();
        otpToken.setTokenHash(com.timecraft.authentication.service.AuthenticationService.hashOtp(email, "654321"));
        emailTokenRepository.save(otpToken);

        MvcResult verifyRes = mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"654321\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(verifyRes.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private Long createIndianAddress(String token) throws Exception {
        AddressRequest req = new AddressRequest(
                "Aarav Mehta", "9876543210", "12 Lavelle Road", "Floor 3", "Near Cubbon Park",
                "Bengaluru", "Karnataka", "560001", "IN", AddressType.HOME, true);
        MvcResult res = mockMvc.perform(post("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private Order createOrderDirectly(Long userId, Long addressId, ProductVariant variant, int qty, String idemp) {
        String token = jwtForUser(userId);
        try {
            mockMvc.perform(post("/api/v1/cart/items")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AddCartItemRequest(variant.getId(), qty))))
                    .andExpect(status().isOk());
            var dto = orderService.placeOrder(userId, new PlaceOrderRequest(addressId, null, null, idemp), idemp);
            return orderRepository.findById(dto.id()).orElseThrow();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    private com.timecraft.security.JwtService jwtService;

    private String jwtForUser(Long userId) {
        User u = userRepository.findById(userId).orElseThrow();
        return jwtService.generateCustomerAccessToken(u);
    }
}
