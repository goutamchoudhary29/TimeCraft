package com.timecraft.customer.controller;

import com.timecraft.address.service.AddressService;
import com.timecraft.address.service.AddressService.AddressDto;
import com.timecraft.address.service.AddressService.AddressRequest;
import com.timecraft.admin.service.StoreConfigService;
import com.timecraft.admin.service.StoreConfigService.ContactTicketRequest;
import com.timecraft.admin.service.StoreConfigService.NewsletterRequest;
import com.timecraft.admin.service.StoreConfigService.SupportTicketDto;
import com.timecraft.common.dto.MessageResponse;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.customer.service.CustomerService;
import com.timecraft.customer.service.CustomerService.ProfileDto;
import com.timecraft.customer.service.CustomerService.UpdateProfileRequest;
import com.timecraft.notification.service.NotificationService;
import com.timecraft.notification.service.NotificationService.NotificationDto;
import com.timecraft.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Customer Account & Storefront")
public class CustomerAccountController {

    private final CustomerService customerService;
    private final AddressService addressService;
    private final NotificationService notificationService;
    private final StoreConfigService storeConfigService;

    @GetMapping("/customer/profile")
    public ResponseEntity<ProfileDto> getProfile() {
        return ResponseEntity.ok(customerService.getProfile(SecurityUtils.requireUserId()));
    }

    @PutMapping("/customer/profile")
    public ResponseEntity<ProfileDto> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(customerService.updateProfile(SecurityUtils.requireUserId(), request));
    }

    @GetMapping("/addresses")
    public ResponseEntity<List<AddressDto>> listAddresses() {
        return ResponseEntity.ok(addressService.listForUser(SecurityUtils.requireUserId()));
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressDto> createAddress(@Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addressService.create(SecurityUtils.requireUserId(), request));
    }

    @PutMapping("/addresses/{id}")
    public ResponseEntity<AddressDto> updateAddress(@PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(addressService.update(SecurityUtils.requireUserId(), id, request));
    }

    @PatchMapping("/addresses/{id}/default")
    public ResponseEntity<AddressDto> setDefaultAddress(@PathVariable Long id) {
        return ResponseEntity.ok(addressService.setDefault(SecurityUtils.requireUserId(), id));
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<MessageResponse> deleteAddress(@PathVariable Long id) {
        addressService.delete(SecurityUtils.requireUserId(), id);
        return ResponseEntity.ok(MessageResponse.of("Address deleted"));
    }

    @GetMapping("/notifications")
    public ResponseEntity<PageResponse<NotificationDto>> listNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(notificationService.listForUser(
                SecurityUtils.requireUserId(), PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)))));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> unreadNotifications() {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.countUnread(SecurityUtils.requireUserId())));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<NotificationDto> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markRead(SecurityUtils.requireUserId(), id));
    }

    @GetMapping("/store/config")
    public ResponseEntity<Map<String, String>> getStoreConfig() {
        return ResponseEntity.ok(storeConfigService.getPublicStorefrontConfig());
    }

    @PostMapping("/store/newsletter")
    public ResponseEntity<MessageResponse> subscribeNewsletter(@Valid @RequestBody NewsletterRequest request) {
        storeConfigService.subscribeNewsletter(request);
        return ResponseEntity.ok(MessageResponse.of("Subscribed to the TimeCraft journal"));
    }

    @PostMapping("/support/contact")
    public ResponseEntity<SupportTicketDto> submitSupportTicket(@Valid @RequestBody ContactTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeConfigService.submitTicket(request));
    }
}
