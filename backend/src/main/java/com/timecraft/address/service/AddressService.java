package com.timecraft.address.service;

import com.timecraft.address.entity.AddressType;
import com.timecraft.address.entity.CustomerAddress;
import com.timecraft.address.repository.CustomerAddressRepository;
import com.timecraft.admin.service.StoreConfigService;
import com.timecraft.authentication.entity.User;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AddressService {

    private static final Pattern IN_PINCODE = Pattern.compile("^[1-9][0-9]{5}$");
    private static final Pattern IN_PHONE = Pattern.compile("^(\\+91[\\-\\s]?)?[6-9][0-9]{9}$");
    private static final Pattern INTL_POSTAL = Pattern.compile("^[A-Za-z0-9\\-\\s]{3,12}$");
    private static final Pattern INTL_PHONE = Pattern.compile("^\\+?[0-9 ()\\-]{7,20}$");

    private final CustomerAddressRepository addressRepository;
    private final UserRepository userRepository;
    private final StoreConfigService storeConfigService;

    public record AddressRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(max = 255) String line1,
            @Size(max = 255) String line2,
            @Size(max = 120) String landmark,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @NotBlank @Size(max = 12) String postalCode,
            @Size(min = 2, max = 2) String country,
            AddressType addressType,
            boolean defaultAddress
    ) {
    }

    public record AddressDto(
            Long id,
            String fullName,
            String phone,
            String line1,
            String line2,
            String landmark,
            String city,
            String state,
            String postalCode,
            String country,
            AddressType addressType,
            boolean defaultAddress
    ) {
        public static AddressDto from(CustomerAddress a) {
            return new AddressDto(a.getId(), a.getFullName(), a.getPhone(), a.getLine1(),
                    a.getLine2(), a.getLandmark(), a.getCity(), a.getState(),
                    a.getPostalCode(), a.getCountry(), a.getAddressType(), a.isDefaultAddress());
        }
    }

    @Transactional(readOnly = true)
    public List<AddressDto> listForUser(Long userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId)
                .stream()
                .map(AddressDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerAddress requireOwnedEntity(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    }

    @Transactional
    public AddressDto create(Long userId, AddressRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        validateAddress(req);

        List<CustomerAddress> current = addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId);
        boolean makeDefault = req.defaultAddress() || current.isEmpty();
        if (makeDefault) {
            addressRepository.clearDefaultForUser(userId);
        }

        CustomerAddress addr = new CustomerAddress();
        apply(addr, req, makeDefault);
        addr.setUser(user);
        return AddressDto.from(addressRepository.save(addr));
    }

    @Transactional
    public AddressDto update(Long userId, Long addressId, AddressRequest req) {
        CustomerAddress addr = requireOwnedEntity(userId, addressId);
        validateAddress(req);
        if (req.defaultAddress()) {
            addressRepository.clearDefaultForUser(userId);
        }
        apply(addr, req, req.defaultAddress() || addr.isDefaultAddress());
        return AddressDto.from(addressRepository.save(addr));
    }

    @Transactional
    public AddressDto setDefault(Long userId, Long addressId) {
        CustomerAddress addr = requireOwnedEntity(userId, addressId);
        addressRepository.clearDefaultForUser(userId);
        addr.setDefaultAddress(true);
        return AddressDto.from(addressRepository.save(addr));
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        CustomerAddress addr = requireOwnedEntity(userId, addressId);
        boolean wasDefault = addr.isDefaultAddress();
        addressRepository.delete(addr);
        if (wasDefault) {
            addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDesc(userId)
                    .stream()
                    .findFirst()
                    .ifPresent(first -> {
                        first.setDefaultAddress(true);
                        addressRepository.save(first);
                    });
        }
    }

    private void validateAddress(AddressRequest req) {
        String country = (req.country() == null || req.country().isBlank() ? "IN" : req.country().trim())
                .toUpperCase(Locale.ROOT);
        String postal = req.postalCode().trim();
        String phone = req.phone().trim();

        if ("IN".equals(country)) {
            if (!IN_PINCODE.matcher(postal).matches()) {
                throw BusinessException.badRequest("INVALID_POSTAL_CODE",
                        "Indian PIN code must be 6 digits and cannot start with 0");
            }
            if (!IN_PHONE.matcher(phone.replaceAll("\\s+", "")).matches()) {
                throw BusinessException.badRequest("INVALID_PHONE",
                        "Indian mobile number must be 10 digits starting with 6-9 (optional +91)");
            }
        } else {
            boolean intlEnabled = storeConfigService.getBoolean("shipping.international_enabled", false);
            if (!intlEnabled) {
                throw BusinessException.badRequest("INTERNATIONAL_SHIPPING_DISABLED",
                        "International shipping is currently disabled in store settings");
            }
            if (!INTL_POSTAL.matcher(postal).matches()) {
                throw BusinessException.badRequest("INVALID_POSTAL_CODE", "Invalid international postal code");
            }
            if (!INTL_PHONE.matcher(phone).matches()) {
                throw BusinessException.badRequest("INVALID_PHONE", "Invalid international phone number");
            }
        }
    }

    private void apply(CustomerAddress target, AddressRequest req, boolean isDefault) {
        String country = (req.country() == null || req.country().isBlank() ? "IN" : req.country().trim())
                .toUpperCase(Locale.ROOT);
        target.setFullName(req.fullName().trim());
        target.setPhone(req.phone().trim());
        target.setLine1(req.line1().trim());
        target.setLine2(req.line2() != null && !req.line2().isBlank() ? req.line2().trim() : null);
        target.setLandmark(req.landmark() != null && !req.landmark().isBlank() ? req.landmark().trim() : null);
        target.setCity(req.city().trim());
        target.setState(req.state().trim());
        target.setPostalCode(req.postalCode().trim());
        target.setCountry(country);
        target.setAddressType(req.addressType() != null ? req.addressType() : AddressType.HOME);
        target.setDefaultAddress(isDefault);
    }
}
