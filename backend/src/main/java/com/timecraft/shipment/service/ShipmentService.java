package com.timecraft.shipment.service;

import com.timecraft.audit.service.AuditLogService;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.notification.service.NotificationService;
import com.timecraft.order.entity.Order;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.entity.PaymentStatus;
import com.timecraft.order.repository.OrderRepository;
import com.timecraft.order.service.OrderService;
import com.timecraft.shipment.entity.Shipment;
import com.timecraft.shipment.entity.ShipmentStatus;
import com.timecraft.shipment.entity.ShipmentTrackingEvent;
import com.timecraft.shipment.repository.ShipmentRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Manages order shipment records and tracking event timelines, keeping shipment status
 * and order status synchronized.
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public record UpsertShipmentRequest(
            @NotNull Long orderId,
            @NotBlank @Size(max = 80) String courierName,
            @NotBlank @Size(max = 80) String trackingNumber,
            @Size(max = 500) String trackingUrl,
            ShipmentStatus status,
            LocalDate estimatedDeliveryDate,
            @Size(max = 120) String location,
            @Size(max = 500) String eventDescription
    ) {
    }

    public record TrackingEventDto(
            Long id,
            ShipmentStatus status,
            String location,
            String description,
            Instant eventTime
    ) {
        public static TrackingEventDto from(ShipmentTrackingEvent e) {
            return new TrackingEventDto(e.getId(), e.getStatus(), e.getLocation(), e.getDescription(), e.getEventTime());
        }
    }

    public record ShipmentDto(
            Long id,
            Long orderId,
            String orderNumber,
            OrderStatus orderStatus,
            String courierName,
            String trackingNumber,
            String trackingUrl,
            ShipmentStatus status,
            LocalDate estimatedDeliveryDate,
            Instant shippedAt,
            Instant deliveredAt,
            List<TrackingEventDto> events
    ) {
        public static ShipmentDto from(Shipment s) {
            return new ShipmentDto(
                    s.getId(),
                    s.getOrder().getId(),
                    s.getOrder().getOrderNumber(),
                    s.getOrder().getStatus(),
                    s.getCourierName(),
                    s.getTrackingNumber(),
                    s.getTrackingUrl(),
                    s.getStatus(),
                    s.getEstimatedDeliveryDate(),
                    s.getShippedAt(),
                    s.getDeliveredAt(),
                    s.getEvents().stream().map(TrackingEventDto::from).toList()
            );
        }
    }

    @Transactional
    public ShipmentDto createOrUpdateShipment(UpsertShipmentRequest req) {
        Order order = orderService.requireOrderEntity(req.orderId());
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw BusinessException.badRequest("ORDER_UNPAID", "Cannot ship an unpaid order");
        }

        Shipment shipment = shipmentRepository.findByOrderId(order.getId()).orElseGet(() ->
                Shipment.builder().order(order).build());
        ShipmentStatus status = req.status() != null ? req.status() : ShipmentStatus.IN_TRANSIT;

        shipment.setCourierName(req.courierName().trim());
        shipment.setTrackingNumber(req.trackingNumber().trim());
        shipment.setTrackingUrl(req.trackingUrl() != null && !req.trackingUrl().isBlank() ? req.trackingUrl().trim() : null);
        shipment.setStatus(status);
        shipment.setEstimatedDeliveryDate(req.estimatedDeliveryDate() != null
                ? req.estimatedDeliveryDate()
                : LocalDate.now().plusDays(4));

        Instant now = Instant.now();
        if (shipment.getShippedAt() == null && status != ShipmentStatus.LABEL_CREATED) {
            shipment.setShippedAt(now);
        }
        if (status == ShipmentStatus.DELIVERED && shipment.getDeliveredAt() == null) {
            shipment.setDeliveredAt(now);
        }

        shipment.getEvents().add(ShipmentTrackingEvent.builder()
                .shipment(shipment)
                .status(status)
                .location(req.location() != null && !req.location().isBlank() ? req.location().trim() : "Dispatch Hub")
                .description(req.eventDescription() != null && !req.eventDescription().isBlank()
                        ? req.eventDescription().trim()
                        : "Shipment status updated to " + status)
                .eventTime(now)
                .build());
        shipment = shipmentRepository.save(shipment);

        synchronizeOrderStatus(order, status);
        auditLogService.logCurrentActor("SHIPMENT_UPDATED", "SHIPMENT", shipment.getId(),
                "Order " + order.getOrderNumber() + " tracking=" + shipment.getTrackingNumber() + " status=" + status);
        notificationService.notifyOrderStatusChanged(order,
                "Shipment " + status.name().replace('_', ' '),
                "Courier: " + shipment.getCourierName() + " · Tracking #: " + shipment.getTrackingNumber());

        return ShipmentDto.from(shipment);
    }

    @Transactional(readOnly = true)
    public ShipmentDto trackByOrderNumberOrTrackingNumber(String number) {
        String key = number.trim();
        Shipment shipment = shipmentRepository.findByTrackingNumberIgnoreCase(key)
                .or(() -> orderRepository.findByOrderNumber(key).flatMap(o -> shipmentRepository.findByOrderId(o.getId())))
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", key));
        return ShipmentDto.from(shipment);
    }

    @Transactional(readOnly = true)
    public ShipmentDto getForOrder(Long orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .map(ShipmentDto::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResponse<ShipmentDto> listAllAdmin(Pageable pageable) {
        return PageResponse.of(shipmentRepository.findAllByOrderByCreatedAtDesc(pageable), ShipmentDto::from);
    }

    private void synchronizeOrderStatus(Order order, ShipmentStatus shipmentStatus) {
        OrderStatus current = order.getStatus();
        switch (shipmentStatus) {
            case LABEL_CREATED -> {
                if (current == OrderStatus.CONFIRMED || current == OrderStatus.PROCESSING) {
                    orderService.transitionFromSystem(order, OrderStatus.PACKED, "Shipping label created");
                }
            }
            case IN_TRANSIT -> {
                if (current == OrderStatus.CONFIRMED || current == OrderStatus.PROCESSING) {
                    orderService.transitionFromSystem(order, OrderStatus.PACKED, "Packed for dispatch");
                    orderService.transitionFromSystem(order, OrderStatus.SHIPPED, "Handed over to courier");
                } else if (current == OrderStatus.PACKED) {
                    orderService.transitionFromSystem(order, OrderStatus.SHIPPED, "Handed over to courier");
                }
            }
            case OUT_FOR_DELIVERY -> {
                if (current == OrderStatus.PACKED) {
                    orderService.transitionFromSystem(order, OrderStatus.SHIPPED, "Dispatched");
                }
                if (order.getStatus() == OrderStatus.SHIPPED) {
                    orderService.transitionFromSystem(order, OrderStatus.OUT_FOR_DELIVERY, "Out for delivery");
                }
            }
            case DELIVERED -> {
                if (current == OrderStatus.PACKED) {
                    orderService.transitionFromSystem(order, OrderStatus.SHIPPED, "Dispatched");
                }
                if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY) {
                    orderService.transitionFromSystem(order, OrderStatus.DELIVERED, "Delivered by courier");
                }
            }
            case DELIVERY_EXCEPTION -> {
                // Keep current order status; tracking timeline records the exception.
            }
        }
    }
}
