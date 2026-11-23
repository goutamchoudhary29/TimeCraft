package com.timecraft.review.repository;

import com.timecraft.review.entity.ProductReview;
import com.timecraft.review.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    Page<ProductReview> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    List<ProductReview> findTop6ByStatusOrderByRatingDescCreatedAtDesc(ReviewStatus status);

    Optional<ProductReview> findByProductIdAndUserId(Long productId, Long userId);

    Optional<ProductReview> findByIdAndUserId(Long id, Long userId);

    List<ProductReview> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<ProductReview> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);

    Page<ProductReview> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(ReviewStatus status);

    @Query("""
            SELECT COALESCE(AVG(r.rating), 0), COUNT(r)
            FROM ProductReview r
            WHERE r.product.id = :productId AND r.status = 'APPROVED'
            """)
    List<Object[]> calculateApprovedStats(@Param("productId") Long productId);
}
