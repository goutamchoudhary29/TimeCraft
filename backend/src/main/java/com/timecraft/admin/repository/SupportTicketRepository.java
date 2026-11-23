package com.timecraft.admin.repository;

import com.timecraft.admin.entity.SupportTicket;
import com.timecraft.admin.entity.SupportTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicketStatus status, Pageable pageable);

    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByStatus(SupportTicketStatus status);
}
