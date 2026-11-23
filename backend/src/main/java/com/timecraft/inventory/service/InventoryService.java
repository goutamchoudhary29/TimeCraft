package com.timecraft.inventory.service;

import com.timecraft.admin.service.StoreConfigService;
import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.inventory.entity.Inventory;
import com.timecraft.inventory.entity.InventoryTransaction;
import com.timecraft.inventory.entity.InventoryTransactionType;
import com.timecraft.inventory.entity.StockReservation;
import com.timecraft.inventory.entity.StockReservationStatus;
import com.timecraft.inventory.repository.InventoryRepository;
import com.timecraft.inventory.repository.InventoryTransactionRepository;
import com.timecraft.inventory.repository.StockReservationRepository;
import com.timecraft.order.entity.Order;
import com.timecraft.product.entity.ProductVariant;
import com.timecraft.product.repository.ProductVariantRepository;
import com.timecraft.security.SecurityUtils;
import com.timecraft.security.TimeCraftPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Variant-level inventory service using pessimistic row locking (SELECT ... FOR UPDATE)
 * to prevent overselling under concurrent purchases, plus expiring stock reservations
 * and an immutable inventory_transactions ledger.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final StockReservationRepository reservationRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final StoreConfigService storeConfigService;
    private final AuditLogService auditLogService;

    public record InventoryAdjustmentRequest(
            @NotNull Long variantId,
            @NotNull InventoryTransactionType type,
            int quantity,
            Integer lowStockThreshold,
            String note
    ) {
    }

    public record InventoryDto(
            Long id,
            Long variantId,
            String variantSku,
            String variantName,
            Long productId,
            String productName,
            int quantityOnHand,
            int quantityReserved,
            int availableQuantity,
            int lowStockThreshold,
            boolean lowStock,
            Instant updatedAt
    ) {
        public static InventoryDto from(Inventory i) {
            ProductVariant v = i.getVariant();
            return new InventoryDto(
                    i.getId(),
                    v.getId(),
                    v.getSku(),
                    v.getName(),
                    v.getProduct().getId(),
                    v.getProduct().getName(),
                    i.getQuantityOnHand(),
                    i.getQuantityReserved(),
                    i.getAvailableQuantity(),
                    i.getLowStockThreshold(),
                    i.isLowStock(),
                    i.getUpdatedAt()
            );
        }
    }

    public record InventoryTransactionDto(
            Long id,
            Long variantId,
            String variantSku,
            String productName,
            InventoryTransactionType transactionType,
            int quantityChange,
            int onHandAfter,
            int reservedAfter,
            String referenceType,
            Long referenceId,
            String note,
            String performedByEmail,
            Instant createdAt
    ) {
        public static InventoryTransactionDto from(InventoryTransaction tx) {
            return new InventoryTransactionDto(
                    tx.getId(),
                    tx.getVariant().getId(),
                    tx.getVariant().getSku(),
                    tx.getVariant().getProduct().getName(),
                    tx.getTransactionType(),
                    tx.getQuantityChange(),
                    tx.getOnHandAfter(),
                    tx.getReservedAfter(),
                    tx.getReferenceType(),
                    tx.getReferenceId(),
                    tx.getNote(),
                    tx.getPerformedBy() != null ? tx.getPerformedBy().getEmail() : "system",
                    tx.getCreatedAt()
            );
        }
    }

    @Transactional
    public Inventory ensureForVariant(ProductVariant variant, int openingStock, Integer threshold) {
        return inventoryRepository.findByVariantId(variant.getId()).orElseGet(() -> {
            int defaultThreshold = threshold != null ? threshold
                    : storeConfigService.getInt("inventory.low_stock_threshold", 5);
            int initial = Math.max(0, openingStock);
            Inventory inv = Inventory.builder()
                    .variant(variant)
                    .quantityOnHand(initial)
                    .quantityReserved(0)
                    .lowStockThreshold(defaultThreshold)
                    .build();
            inv = inventoryRepository.save(inv);
            if (initial > 0) {
                recordTransaction(variant, InventoryTransactionType.STOCK_IN, initial, inv, "OPENING", variant.getId(), "Opening stock");
            }
            return inv;
        });
    }

    @Transactional(readOnly = true)
    public int getAvailableForVariant(Long variantId) {
        return inventoryRepository.findByVariantId(variantId)
                .map(Inventory::getAvailableQuantity)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> getAvailableByVariantIds(Collection<Long> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findByVariantIdIn(variantIds).stream()
                .collect(Collectors.toMap(i -> i.getVariant().getId(), Inventory::getAvailableQuantity));
    }

    @Transactional
    public StockReservation reserveForOrder(Order order, ProductVariant variant, int quantity, Duration ttl) {
        if (quantity <= 0) {
            throw BusinessException.badRequest("INVALID_QUANTITY", "Quantity must be positive");
        }
        Inventory inv = inventoryRepository.findByVariantIdForUpdate(variant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for variant", variant.getSku()));
        if (inv.getAvailableQuantity() < quantity) {
            throw BusinessException.conflict("INSUFFICIENT_STOCK",
                    "Only " + inv.getAvailableQuantity() + " unit(s) available for "
                            + variant.getProduct().getName() + " (" + variant.getName() + ")");
        }
        inv.setQuantityReserved(inv.getQuantityReserved() + quantity);
        inventoryRepository.save(inv);

        StockReservation reservation = StockReservation.builder()
                .variant(variant)
                .order(order)
                .quantity(quantity)
                .status(StockReservationStatus.ACTIVE)
                .expiresAt(Instant.now().plus(ttl))
                .build();
        reservation = reservationRepository.save(reservation);

        recordTransaction(variant, InventoryTransactionType.RESERVE, -quantity, inv,
                "ORDER", order.getId(), "Reserved for order " + order.getOrderNumber());
        return reservation;
    }

    @Transactional
    public void commitReservationsForOrder(Order order) {
        List<StockReservation> reservations = reservationRepository.findByOrderId(order.getId());
        for (StockReservation r : reservations) {
            if (r.getStatus() == StockReservationStatus.COMMITTED) {
                continue;
            }
            Inventory inv = inventoryRepository.findByVariantIdForUpdate(r.getVariant().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", r.getVariant().getId()));
            int qty = r.getQuantity();
            if (r.getStatus() == StockReservationStatus.ACTIVE) {
                if (inv.getQuantityOnHand() < qty || inv.getQuantityReserved() < qty) {
                    throw BusinessException.conflict("STOCK_COMMIT_CONFLICT",
                            "Unable to commit stock reservation for variant " + r.getVariant().getSku());
                }
                inv.setQuantityReserved(inv.getQuantityReserved() - qty);
                inv.setQuantityOnHand(inv.getQuantityOnHand() - qty);
            } else {
                // Delayed webhook after reservation expiry: verify on-hand stock is still available.
                if (inv.getAvailableQuantity() < qty) {
                    throw BusinessException.conflict("INSUFFICIENT_STOCK_AFTER_EXPIRY",
                            "Stock for " + r.getVariant().getSku() + " was sold after reservation expired");
                }
                inv.setQuantityOnHand(inv.getQuantityOnHand() - qty);
            }
            inventoryRepository.save(inv);
            r.setStatus(StockReservationStatus.COMMITTED);
            reservationRepository.save(r);

            recordTransaction(r.getVariant(), InventoryTransactionType.COMMIT, -qty, inv,
                    "ORDER", order.getId(), "Committed stock on payment confirmation for " + order.getOrderNumber());
        }
    }

    @Transactional
    public void releaseReservationsForOrder(Order order, StockReservationStatus targetStatus, String reason) {
        List<StockReservation> active = reservationRepository.findByOrderIdAndStatus(
                order.getId(), StockReservationStatus.ACTIVE);
        for (StockReservation r : active) {
            Inventory inv = inventoryRepository.findByVariantIdForUpdate(r.getVariant().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", r.getVariant().getId()));
            inv.setQuantityReserved(Math.max(0, inv.getQuantityReserved() - r.getQuantity()));
            inventoryRepository.save(inv);
            r.setStatus(targetStatus);
            reservationRepository.save(r);

            recordTransaction(r.getVariant(), InventoryTransactionType.RELEASE, r.getQuantity(), inv,
                    "ORDER", order.getId(), reason);
        }
    }

    @Transactional
    public void restoreCommittedStockForOrder(Order order, String reason) {
        List<StockReservation> committed = reservationRepository.findByOrderIdAndStatus(
                order.getId(), StockReservationStatus.COMMITTED);
        for (StockReservation r : committed) {
            Inventory inv = inventoryRepository.findByVariantIdForUpdate(r.getVariant().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", r.getVariant().getId()));
            inv.setQuantityOnHand(inv.getQuantityOnHand() + r.getQuantity());
            inventoryRepository.save(inv);
            r.setStatus(StockReservationStatus.RELEASED);
            reservationRepository.save(r);

            recordTransaction(r.getVariant(), InventoryTransactionType.RETURN_RESTORE, r.getQuantity(), inv,
                    "ORDER", order.getId(), reason);
        }
    }

    @Transactional
    public void restoreVariantQuantity(ProductVariant variant, int quantity, String referenceType, Long referenceId, String note) {
        if (quantity <= 0 || variant == null) {
            return;
        }
        Inventory inv = inventoryRepository.findByVariantIdForUpdate(variant.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", variant.getId()));
        inv.setQuantityOnHand(inv.getQuantityOnHand() + quantity);
        inventoryRepository.save(inv);
        recordTransaction(variant, InventoryTransactionType.RETURN_RESTORE, quantity, inv, referenceType, referenceId, note);
    }

    @Transactional
    public InventoryDto adjustStock(InventoryAdjustmentRequest req) {
        ProductVariant variant = variantRepository.findByIdAndDeletedFalse(req.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variant", req.variantId()));
        Inventory inv = inventoryRepository.findByVariantIdForUpdate(variant.getId())
                .orElseGet(() -> ensureForVariant(variant, 0, req.lowStockThreshold()));

        int change;
        switch (req.type()) {
            case STOCK_IN -> {
                if (req.quantity() <= 0) {
                    throw BusinessException.badRequest("INVALID_QUANTITY", "Stock-in quantity must be positive");
                }
                change = req.quantity();
                inv.setQuantityOnHand(inv.getQuantityOnHand() + change);
            }
            case STOCK_OUT -> {
                if (req.quantity() <= 0) {
                    throw BusinessException.badRequest("INVALID_QUANTITY", "Stock-out quantity must be positive");
                }
                if (inv.getAvailableQuantity() < req.quantity()) {
                    throw BusinessException.badRequest("NEGATIVE_STOCK_NOT_ALLOWED",
                            "Cannot reduce stock below reserved/available quantity (" + inv.getAvailableQuantity() + ")");
                }
                change = -req.quantity();
                inv.setQuantityOnHand(inv.getQuantityOnHand() - req.quantity());
            }
            case ADJUSTMENT -> {
                if (req.quantity() < inv.getQuantityReserved()) {
                    throw BusinessException.badRequest("NEGATIVE_STOCK_NOT_ALLOWED",
                            "New on-hand quantity cannot be less than currently reserved quantity (" + inv.getQuantityReserved() + ")");
                }
                change = req.quantity() - inv.getQuantityOnHand();
                inv.setQuantityOnHand(req.quantity());
            }
            default -> throw BusinessException.badRequest("UNSUPPORTED_ADJUSTMENT",
                    "Only STOCK_IN, STOCK_OUT and ADJUSTMENT are allowed for manual stock adjustments");
        }

        if (req.lowStockThreshold() != null && req.lowStockThreshold() >= 0) {
            inv.setLowStockThreshold(req.lowStockThreshold());
        }
        inv = inventoryRepository.save(inv);
        recordTransaction(variant, req.type(), change, inv, "MANUAL", variant.getId(),
                req.note() != null ? req.note() : "Admin stock adjustment");
        auditLogService.logCurrentActor("INVENTORY_ADJUSTED", "INVENTORY", inv.getId(),
                "Variant " + variant.getSku() + " " + req.type() + " change=" + change + " onHand=" + inv.getQuantityOnHand());
        return InventoryDto.from(inv);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryDto> searchInventory(String search, boolean lowOnly, Pageable pageable) {
        String q = search != null && !search.isBlank() ? search.trim() : null;
        return PageResponse.of(inventoryRepository.searchInventory(q, lowOnly, pageable), InventoryDto::from);
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> listLowStock() {
        return inventoryRepository.findLowStockItems().stream().map(InventoryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionDto> listTransactions(Long variantId, Pageable pageable) {
        var page = variantId != null
                ? transactionRepository.findByVariantIdOrderByCreatedAtDesc(variantId, pageable)
                : transactionRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.of(page, InventoryTransactionDto::from);
    }

    private void recordTransaction(ProductVariant variant, InventoryTransactionType type, int change,
                                   Inventory inv, String refType, Long refId, String note) {
        User actor = SecurityUtils.currentPrincipal()
                .map(TimeCraftPrincipal::getId)
                .flatMap(userRepository::findById)
                .orElse(null);
        InventoryTransaction tx = InventoryTransaction.builder()
                .variant(variant)
                .transactionType(type)
                .quantityChange(change)
                .onHandAfter(inv.getQuantityOnHand())
                .reservedAfter(inv.getQuantityReserved())
                .referenceType(refType)
                .referenceId(refId)
                .note(note)
                .performedBy(actor)
                .build();
        transactionRepository.save(tx);
    }
}
