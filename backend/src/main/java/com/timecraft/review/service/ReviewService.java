package com.timecraft.review.service;

import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.common.util.Money;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.order.entity.OrderItem;
import com.timecraft.order.repository.OrderItemRepository;
import com.timecraft.product.entity.Product;
import com.timecraft.product.repository.ProductRepository;
import com.timecraft.product.service.CloudinaryService;
import com.timecraft.review.entity.ProductReview;
import com.timecraft.review.entity.ReviewImage;
import com.timecraft.review.entity.ReviewStatus;
import com.timecraft.review.repository.ProductReviewRepository;
import com.timecraft.security.SecurityUtils;
import com.timecraft.security.TimeCraftPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Verified-purchase review service with one-review-per-customer-per-product enforcement
 * and admin moderation workflow.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final AuditLogService auditLogService;

    public record SubmitReviewRequest(
            @NotNull Long productId,
            @Min(1) @Max(5) int rating,
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 4000) String body,
            List<@Size(max = 500) String> imageUrls
    ) {
    }

    public record UpdateReviewRequest(
            @Min(1) @Max(5) int rating,
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 4000) String body
    ) {
    }

    public record ModerateReviewRequest(
            @NotNull ReviewStatus status,
            @Size(max = 500) String moderationNote
    ) {
    }

    public record ReviewDto(
            Long id,
            Long productId,
            String productName,
            String productSlug,
            Long userId,
            String reviewerName,
            int rating,
            String title,
            String body,
            boolean verifiedPurchase,
            ReviewStatus status,
            String moderationNote,
            List<String> images,
            Instant createdAt
    ) {
        public static ReviewDto from(ProductReview r) {
            return new ReviewDto(
                    r.getId(),
                    r.getProduct().getId(),
                    r.getProduct().getName(),
                    r.getProduct().getSlug(),
                    r.getUser().getId(),
                    r.getUser().getFirstName() + " " + r.getUser().getLastName().charAt(0) + ".",
                    r.getRating(),
                    r.getTitle(),
                    r.getBody(),
                    r.isVerifiedPurchase(),
                    r.getStatus(),
                    r.getModerationNote(),
                    r.getImages().stream().map(ReviewImage::getUrl).toList(),
                    r.getCreatedAt()
            );
        }
    }

    @Transactional
    public ReviewDto submitReview(Long userId, SubmitReviewRequest req) {
        Product product = productRepository.findByIdAndDeletedFalse(req.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", req.productId()));
        if (reviewRepository.findByProductIdAndUserId(product.getId(), userId).isPresent()) {
            throw BusinessException.conflict("DUPLICATE_REVIEW",
                    "You have already submitted a review for this timepiece");
        }

        List<OrderItem> deliveredItems = orderItemRepository.findDeliveredItemsForUserAndProduct(userId, product.getId());
        if (deliveredItems.isEmpty()) {
            throw BusinessException.forbidden(
                    "Only verified purchasers with a delivered order containing this timepiece may submit a review");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        ProductReview review = ProductReview.builder()
                .product(product)
                .user(user)
                .orderItem(deliveredItems.get(0))
                .rating(req.rating())
                .title(req.title().trim())
                .body(req.body().trim())
                .verifiedPurchase(true)
                .status(ReviewStatus.PENDING)
                .build();

        if (req.imageUrls() != null) {
            req.imageUrls().stream()
                    .filter(u -> u != null && !u.isBlank())
                    .limit(4)
                    .forEach(url -> review.getImages().add(ReviewImage.builder()
                            .review(review)
                            .url(url.trim())
                            .build()));
        }

        return ReviewDto.from(reviewRepository.save(review));
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public ReviewDto updateOwnReview(Long userId, Long reviewId, UpdateReviewRequest req) {
        ProductReview review = reviewRepository.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        review.setRating(req.rating());
        review.setTitle(req.title().trim());
        review.setBody(req.body().trim());
        review.setStatus(ReviewStatus.PENDING);
        review = reviewRepository.save(review);
        recalculateProductRating(review.getProduct().getId());
        return ReviewDto.from(review);
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public void deleteOwnReview(Long userId, Long reviewId) {
        ProductReview review = reviewRepository.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        Long productId = review.getProduct().getId();
        review.getImages().forEach(img -> cloudinaryService.deleteIfConfigured(img.getPublicId()));
        reviewRepository.delete(review);
        recalculateProductRating(productId);
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public ReviewDto moderateReview(Long reviewId, ModerateReviewRequest req) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        User moderator = SecurityUtils.currentPrincipal()
                .map(TimeCraftPrincipal::getId)
                .flatMap(userRepository::findById)
                .orElse(null);

        review.setStatus(req.status());
        review.setModerationNote(req.moderationNote());
        review.setModeratedBy(moderator);
        review.setModeratedAt(Instant.now());
        review = reviewRepository.save(review);

        recalculateProductRating(review.getProduct().getId());
        auditLogService.logCurrentActor("REVIEW_MODERATED", "PRODUCT_REVIEW", reviewId,
                "Review " + reviewId + " moderated to " + req.status());
        return ReviewDto.from(review);
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public void deleteByAdmin(Long reviewId) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        Long productId = review.getProduct().getId();
        review.getImages().forEach(img -> cloudinaryService.deleteIfConfigured(img.getPublicId()));
        reviewRepository.delete(review);
        recalculateProductRating(productId);
        auditLogService.logCurrentActor("REVIEW_DELETED", "PRODUCT_REVIEW", reviewId,
                "Deleted review " + reviewId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> listApprovedForProduct(Long productId, String sort, int page, int size) {
        Sort sortSpec = switch (sort != null ? sort.toLowerCase(Locale.ROOT) : "newest") {
            case "rating_desc", "highest" -> Sort.by(Sort.Direction.DESC, "rating", "createdAt");
            case "rating_asc", "lowest" -> Sort.by(Sort.Direction.ASC, "rating", "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)), sortSpec);
        return PageResponse.of(
                reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED, pageable),
                ReviewDto::from
        );
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> listFeaturedApproved() {
        return reviewRepository.findTop6ByStatusOrderByRatingDescCreatedAtDesc(ReviewStatus.APPROVED)
                .stream()
                .map(ReviewDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> listForUser(Long userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ReviewDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> listAdmin(ReviewStatus status, Pageable pageable) {
        var page = status != null
                ? reviewRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : reviewRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.of(page, ReviewDto::from);
    }

    private void recalculateProductRating(Long productId) {
        productRepository.findById(productId).ifPresent(product -> {
            List<Object[]> rows = reviewRepository.calculateApprovedStats(productId);
            if (rows.isEmpty() || rows.get(0) == null) {
                product.setAvgRating(Money.ZERO);
                product.setReviewCount(0);
            } else {
                Object[] row = rows.get(0);
                double avg = row[0] instanceof Number n ? n.doubleValue() : 0.0;
                int count = row[1] instanceof Number n ? n.intValue() : 0;
                product.setAvgRating(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
                product.setReviewCount(count);
            }
            productRepository.save(product);
        });
    }
}
