package com.timecraft.review.controller;

import com.timecraft.common.dto.MessageResponse;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.returns.service.ReturnAndRefundService;
import com.timecraft.returns.service.ReturnAndRefundService.CreateReturnRequest;
import com.timecraft.returns.service.ReturnAndRefundService.ReturnRequestDto;
import com.timecraft.review.service.ReviewService;
import com.timecraft.review.service.ReviewService.ReviewDto;
import com.timecraft.review.service.ReviewService.SubmitReviewRequest;
import com.timecraft.review.service.ReviewService.UpdateReviewRequest;
import com.timecraft.security.SecurityUtils;
import com.timecraft.shipment.service.ShipmentService;
import com.timecraft.shipment.service.ShipmentService.ShipmentDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostPurchaseController {

    private final ShipmentService shipSvc;
    private final ReturnAndRefundService returnSvc;
    private final ReviewService reviewSvc;

    @GetMapping("/shipments/track/{number}")
    public ShipmentDto track(@PathVariable String number) {
        return shipSvc.trackByOrderNumberOrTrackingNumber(number);
    }

    @GetMapping("/returns")
    public List<ReturnRequestDto> myReturns() {
        return returnSvc.listCustomerReturns(SecurityUtils.requireUserId());
    }

    @PostMapping("/returns")
    public ResponseEntity<ReturnRequestDto> requestReturn(@Valid @RequestBody CreateReturnRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(returnSvc.submitReturnRequest(SecurityUtils.requireUserId(), body));
    }

    @GetMapping("/products/{productId}/reviews")
    public PageResponse<ReviewDto> productReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return reviewSvc.listApprovedForProduct(productId, sort, page, size);
    }

    @GetMapping("/reviews/featured")
    public List<ReviewDto> featuredReviews() {
        return reviewSvc.listFeaturedApproved();
    }

    @GetMapping("/reviews/me")
    public List<ReviewDto> myReviews() {
        return reviewSvc.listForUser(SecurityUtils.requireUserId());
    }

    @PostMapping("/reviews")
    public ResponseEntity<ReviewDto> createReview(@Valid @RequestBody SubmitReviewRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewSvc.submitReview(SecurityUtils.requireUserId(), body));
    }

    @PutMapping("/reviews/{id}")
    public ReviewDto editReview(@PathVariable Long id, @Valid @RequestBody UpdateReviewRequest body) {
        return reviewSvc.updateOwnReview(SecurityUtils.requireUserId(), id, body);
    }

    @DeleteMapping("/reviews/{id}")
    public MessageResponse deleteReview(@PathVariable Long id) {
        reviewSvc.deleteOwnReview(SecurityUtils.requireUserId(), id);
        return MessageResponse.of("Review deleted");
    }
}
