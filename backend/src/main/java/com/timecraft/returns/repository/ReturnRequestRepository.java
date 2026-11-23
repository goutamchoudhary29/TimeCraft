package com.timecraft.returns.repository;

import com.timecraft.returns.entity.ReturnRequest;
import com.timecraft.returns.entity.ReturnRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ReturnRequest> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<ReturnRequest> findByIdAndUserId(Long id, Long userId);

    Page<ReturnRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ReturnRequest> findByStatusOrderByCreatedAtDesc(ReturnRequestStatus status, Pageable pageable);

    long countByStatus(ReturnRequestStatus status);
}
