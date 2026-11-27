package com.timecraft.delivery.service;

import com.timecraft.admin.service.StoreConfigService;
import com.timecraft.audit.service.AuditLogService;
import com.timecraft.delivery.dto.DeliveryDtos.DeliveryEstimateDto;
import com.timecraft.delivery.dto.DeliveryDtos.HolidayRequest;
import com.timecraft.delivery.dto.DeliveryDtos.PinRuleRequest;
import com.timecraft.delivery.dto.DeliveryDtos.ProductDeliveryConfigDto;
import com.timecraft.delivery.dto.DeliveryDtos.ProductDeliveryConfigRequest;
import com.timecraft.delivery.dto.DeliveryDtos.WarehouseRequest;
import com.timecraft.delivery.entity.DeliveryHoliday;
import com.timecraft.delivery.entity.DeliveryPinRule;
import com.timecraft.delivery.entity.DeliveryWarehouse;
import com.timecraft.delivery.entity.DeliveryZoneType;
import com.timecraft.delivery.entity.FulfillmentType;
import com.timecraft.delivery.entity.ProductDeliveryConfig;
import com.timecraft.delivery.repository.DeliveryHolidayRepository;
import com.timecraft.delivery.repository.DeliveryPinRuleRepository;
import com.timecraft.delivery.repository.DeliveryWarehouseRepository;
import com.timecraft.delivery.repository.ProductDeliveryConfigRepository;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.inventory.service.InventoryService;
import com.timecraft.product.entity.Product;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.entity.ProductVariant;
import com.timecraft.product.repository.ProductRepository;
import com.timecraft.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dynamic, backend-driven horological delivery estimation engine.
 * Computes realistic dispatch and delivery windows based on customer PIN code,
 * stock & fulfillment condition, dispatch warehouse, processing time, courier rules,
 * weekends, and MySQL-configured public holiday exclusions.
 */
@Service
@RequiredArgsConstructor
public class DeliveryEstimationService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Pattern INDIAN_PIN_PATTERN = Pattern.compile("^[1-9][0-9]{5}$");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);
    private static final int LOW_STOCK_THRESHOLD = 3;

    private final DeliveryWarehouseRepository warehouseRepository;
    private final DeliveryPinRuleRepository pinRuleRepository;
    private final DeliveryHolidayRepository holidayRepository;
    private final ProductDeliveryConfigRepository productConfigRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;
    private final StoreConfigService storeConfigService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public DeliveryEstimateDto estimateByProductLookup(Long productId, String slug, Long variantId, String rawPinCode) {
        Product product;
        if (productId != null) {
            product = productRepository.findByIdAndDeletedFalse(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        } else if (slug != null && !slug.isBlank()) {
            product = productRepository.findBySlugAndDeletedFalse(slug.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", slug));
        } else {
            throw BusinessException.badRequest("PRODUCT_REQUIRED", "Provide productId or slug to estimate delivery");
        }

        int stock;
        if (variantId != null) {
            ProductVariant variant = variantRepository.findByIdAndDeletedFalse(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
            stock = inventoryService.getAvailableForVariant(variant.getId());
        } else {
            List<Long> varIds = product.getVariants().stream()
                    .filter(v -> !v.isDeleted() && v.isActive())
                    .map(ProductVariant::getId)
                    .toList();
            Map<Long, Integer> stockMap = inventoryService.getAvailableByVariantIds(varIds);
            stock = stockMap.values().stream().mapToInt(Integer::intValue).sum();
        }

        return estimateForProduct(product, stock, rawPinCode);
    }

    @Transactional(readOnly = true)
    public DeliveryEstimateDto estimateForProduct(Product product, int availableStock, String rawPinCode) {
        String pinCode = normalizeAndValidatePin(rawPinCode);
        ProductDeliveryConfig cfg = productConfigRepository.findById(product.getId()).orElse(null);
        DeliveryWarehouse warehouse = resolveWarehouse(cfg);

        FulfillmentType fulfillmentType = cfg != null && cfg.getFulfillmentType() != null
                ? cfg.getFulfillmentType()
                : FulfillmentType.IN_STOCK;

        BigDecimal freeThreshold = storeConfigService.getDecimal("shipping.free_threshold", "10000.00");
        BigDecimal flatShipping = storeConfigService.getDecimal("shipping.flat_rate", "250.00");
        String defaultCourier = storeConfigService.getString("delivery.default_courier", "BlueDart Armoured Vault");
        boolean excludeWeekends = storeConfigService.getBoolean("delivery.exclude_weekends", true);
        int cutoffHour = storeConfigService.getInt("delivery.cutoff_hour_ist", 16);
        Set<LocalDate> holidays = holidayRepository.findAllByOrderByHolidayDateAsc().stream()
                .map(DeliveryHoliday::getHolidayDate)
                .collect(Collectors.toSet());

        String warehouseCode = warehouse != null ? warehouse.getCode() : "BLR-VAULT";
        String warehouseName = warehouse != null ? warehouse.getName() : "Bengaluru Horology Vault";
        String warehouseCity = warehouse != null ? warehouse.getCity() : "Bengaluru";
        String warehouseState = warehouse != null ? warehouse.getState() : "Karnataka";

        String shippingInfo = product.getSellingPrice() != null && product.getSellingPrice().compareTo(freeThreshold) >= 0
                ? "Complimentary Insured Vault Shipping (" + defaultCourier + ")"
                : "Insured Vault Shipping ₹" + flatShipping.stripTrailingZeros().toPlainString()
                + " (Complimentary above ₹" + freeThreshold.stripTrailingZeros().toPlainString() + ")";

        // 1. Evaluate Stock / Product Availability Condition
        boolean productInactive = product.isDeleted() || product.getStatus() != ProductStatus.ACTIVE;
        if (productInactive || (fulfillmentType == FulfillmentType.IN_STOCK && availableStock <= 0)) {
            return new DeliveryEstimateDto(
                    false,
                    false,
                    "OUT_OF_STOCK",
                    fulfillmentType.name(),
                    pinCode != null ? DeliveryZoneType.STANDARD.name() : DeliveryZoneType.STANDARD.name(),
                    pinCode,
                    warehouseCode,
                    warehouseName,
                    warehouseCity,
                    warehouseState,
                    defaultCourier,
                    0, 0, 0, 0, 0, 0,
                    null, null, null,
                    cfg != null ? cfg.getPreorderReleaseDate() : null,
                    "Currently Out of Stock",
                    "This reference is currently unavailable for dispatch.",
                    shippingInfo
            );
        }

        String stockCondition;
        if (fulfillmentType == FulfillmentType.PRE_ORDER) {
            stockCondition = "PRE_ORDER";
        } else if (fulfillmentType == FulfillmentType.MADE_TO_ORDER) {
            stockCondition = "MADE_TO_ORDER";
        } else if (availableStock <= LOW_STOCK_THRESHOLD) {
            stockCondition = "LOW_STOCK";
        } else {
            stockCondition = "IN_STOCK";
        }

        // 2. Determine PIN Code Zone, Serviceability & Transit Days
        DeliveryZoneType zoneType = DeliveryZoneType.STANDARD;
        String courierName = defaultCourier;
        int shipMin;
        int shipMax;

        if (pinCode != null) {
            DeliveryPinRule matchedRule = findMatchingPinRule(pinCode);
            if (matchedRule != null) {
                if (!matchedRule.isServiceable() || matchedRule.getZoneType() == DeliveryZoneType.UNSERVICEABLE) {
                    return new DeliveryEstimateDto(
                            false,
                            true,
                            stockCondition,
                            fulfillmentType.name(),
                            DeliveryZoneType.UNSERVICEABLE.name(),
                            pinCode,
                            warehouseCode,
                            warehouseName,
                            warehouseCity,
                            warehouseState,
                            null,
                            0, 0, 0, 0, 0, 0,
                            null, null, null,
                            cfg != null ? cfg.getPreorderReleaseDate() : null,
                            "Delivery Unavailable for PIN " + pinCode,
                            matchedRule.getNotes() != null && !matchedRule.getNotes().isBlank()
                                    ? matchedRule.getNotes()
                                    : "Insured courier service is currently unavailable for PIN code " + pinCode + ".",
                            "Please enter an alternate serviceable PIN code or contact TimeCraft Concierge."
                    );
                }
                zoneType = matchedRule.getZoneType();
                if (matchedRule.getCourierName() != null && !matchedRule.getCourierName().isBlank()) {
                    courierName = matchedRule.getCourierName();
                }
                int[] baseZoneDays = getZoneDays(zoneType);
                shipMin = matchedRule.getMinDaysOverride() != null ? matchedRule.getMinDaysOverride() : baseZoneDays[0];
                shipMax = matchedRule.getMaxDaysOverride() != null ? matchedRule.getMaxDaysOverride() : baseZoneDays[1];
            } else {
                zoneType = classifyPinAgainstWarehouse(pinCode, warehouse);
                int[] baseZoneDays = getZoneDays(zoneType);
                shipMin = baseZoneDays[0];
                shipMax = baseZoneDays[1];
            }
        } else {
            // Default estimate shown on cards / initial PDP before PIN entry
            int[] otherState = getZoneDays(DeliveryZoneType.OTHER_STATE);
            shipMin = otherState[0];
            shipMax = otherState[1];
        }

        if (cfg != null && cfg.getShippingDaysMinOverride() != null && cfg.getShippingDaysMaxOverride() != null) {
            shipMin = Math.max(1, cfg.getShippingDaysMinOverride());
            shipMax = Math.max(shipMin, cfg.getShippingDaysMaxOverride());
        }

        // Update shippingInfo with resolved courier
        shippingInfo = product.getSellingPrice() != null && product.getSellingPrice().compareTo(freeThreshold) >= 0
                ? "Complimentary Insured Vault Delivery via " + courierName
                : "Insured Delivery ₹" + flatShipping.stripTrailingZeros().toPlainString()
                + " via " + courierName + " (Free above ₹" + freeThreshold.stripTrailingZeros().toPlainString() + ")";

        // 3. Determine Processing / Manufacturing Days
        int procMin;
        int procMax;
        if (fulfillmentType == FulfillmentType.MADE_TO_ORDER) {
            int defSpecMin = storeConfigService.getInt("delivery.special_order_min_days", 10);
            int defSpecMax = storeConfigService.getInt("delivery.special_order_max_days", 18);
            procMin = cfg != null && cfg.getProcessingDaysMin() != null ? cfg.getProcessingDaysMin() : defSpecMin;
            procMax = cfg != null && cfg.getProcessingDaysMax() != null ? cfg.getProcessingDaysMax() : defSpecMax;
        } else {
            int defProcMin = storeConfigService.getInt("delivery.processing_days_min", 1);
            int defProcMax = storeConfigService.getInt("delivery.processing_days_max", 2);
            procMin = cfg != null && cfg.getProcessingDaysMin() != null ? cfg.getProcessingDaysMin() : defProcMin;
            procMax = cfg != null && cfg.getProcessingDaysMax() != null ? cfg.getProcessingDaysMax() : defProcMax;
        }
        procMin = Math.max(0, procMin);
        procMax = Math.max(procMin, procMax);

        // 4. Calculate Business-Day Dispatch & Delivery Dates (Asia/Kolkata, excluding weekends & public holidays)
        ZonedDateTime nowIst = ZonedDateTime.now(IST);
        LocalDate baseStart = nowIst.toLocalDate();
        if (nowIst.getHour() >= cutoffHour) {
            baseStart = nextBusinessDay(baseStart.plusDays(1), excludeWeekends, holidays);
        } else {
            baseStart = nextBusinessDay(baseStart, excludeWeekends, holidays);
        }

        LocalDate preorderDate = cfg != null ? cfg.getPreorderReleaseDate() : null;
        if (fulfillmentType == FulfillmentType.PRE_ORDER && preorderDate != null && preorderDate.isAfter(baseStart)) {
            baseStart = nextBusinessDay(preorderDate, excludeWeekends, holidays);
        }

        LocalDate expectedDispatchDate = addBusinessDays(baseStart, procMin, excludeWeekends, holidays);
        LocalDate deliveryStartDate = addBusinessDays(baseStart, procMin + shipMin, excludeWeekends, holidays);
        LocalDate deliveryEndDate = addBusinessDays(baseStart, procMax + shipMax, excludeWeekends, holidays);

        // Total displayed business-day range
        int totalMinDays = fulfillmentType == FulfillmentType.MADE_TO_ORDER ? (procMin + shipMin) : shipMin;
        int totalMaxDays = fulfillmentType == FulfillmentType.MADE_TO_ORDER ? (procMax + shipMax) : shipMax;
        if (totalMaxDays < totalMinDays) {
            totalMaxDays = totalMinDays;
        }

        // 5. Compose Concise, Accurate Labels (Never fake guaranteed promises)
        String shortLabel;
        String detailMessage;
        String dateRangeText = SHORT_DATE_FMT.format(deliveryStartDate) + " – " + SHORT_DATE_FMT.format(deliveryEndDate);
        String dispatchText = SHORT_DATE_FMT.format(expectedDispatchDate);

        if (fulfillmentType == FulfillmentType.PRE_ORDER) {
            String availPart = preorderDate != null ? "Available " + SHORT_DATE_FMT.format(preorderDate) : "Pre-Order Allocation";
            shortLabel = "Pre-Order · Est. Delivery: " + dateRangeText;
            detailMessage = availPart + " · Expected dispatch " + dispatchText + " from " + warehouseName
                    + (cfg != null && cfg.getSpecialOrderNote() != null && !cfg.getSpecialOrderNote().isBlank()
                    ? " · " + cfg.getSpecialOrderNote() : "");
        } else if (fulfillmentType == FulfillmentType.MADE_TO_ORDER) {
            shortLabel = "Made to Order · Est. " + totalMinDays + "–" + totalMaxDays + " Business Days";
            detailMessage = "Atelier crafting & regulation (" + procMin + "–" + procMax + " business days), shipping from "
                    + warehouseName + " · Est. " + dateRangeText
                    + (cfg != null && cfg.getSpecialOrderNote() != null && !cfg.getSpecialOrderNote().isBlank()
                    ? " · " + cfg.getSpecialOrderNote() : "");
        } else if (pinCode != null) {
            shortLabel = "Estimated Delivery: " + totalMinDays + "–" + totalMaxDays + " Business Days (" + dateRangeText + ")";
            detailMessage = "Dispatches by " + dispatchText + " from " + warehouseName + " (" + warehouseCity + ") · "
                    + formatZoneLabel(zoneType) + " estimate for PIN " + pinCode;
        } else {
            shortLabel = "Estimated Delivery: " + totalMinDays + "–" + totalMaxDays + " Business Days";
            detailMessage = "Expected dispatch by " + dispatchText + " from " + warehouseName + " (" + warehouseCity + "). Enter your PIN code for an exact estimate.";
        }

        return new DeliveryEstimateDto(
                true,
                true,
                stockCondition,
                fulfillmentType.name(),
                zoneType.name(),
                pinCode,
                warehouseCode,
                warehouseName,
                warehouseCity,
                warehouseState,
                courierName,
                procMin,
                procMax,
                shipMin,
                shipMax,
                totalMinDays,
                totalMaxDays,
                expectedDispatchDate,
                deliveryStartDate,
                deliveryEndDate,
                preorderDate,
                shortLabel,
                detailMessage,
                shippingInfo
        );
    }

    // =========================================================================
    // ADMIN DELIVERY MANAGEMENT CRUD (MySQL-Backed)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<DeliveryWarehouse> listWarehouses() {
        return warehouseRepository.findAllByOrderByDefaultWarehouseDescNameAsc();
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public DeliveryWarehouse saveWarehouse(Long id, WarehouseRequest req) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        DeliveryWarehouse wh = (id != null)
                ? warehouseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Warehouse", id))
                : warehouseRepository.findByCodeIgnoreCase(code).orElseGet(DeliveryWarehouse::new);

        if (req.defaultWarehouse()) {
            warehouseRepository.findAll().forEach(existing -> {
                if (existing.isDefaultWarehouse() && !Objects.equals(existing.getId(), wh.getId())) {
                    existing.setDefaultWarehouse(false);
                    warehouseRepository.save(existing);
                }
            });
        }
        wh.setCode(code);
        wh.setName(req.name().trim());
        wh.setCity(req.city().trim());
        wh.setState(req.state().trim());
        wh.setPinCode(req.pinCode().trim());
        wh.setPinPrefixLocal(req.pinPrefixLocal() != null ? req.pinPrefixLocal().trim() : null);
        wh.setPinPrefixState(req.pinPrefixState() != null ? req.pinPrefixState().trim() : null);
        wh.setDefaultWarehouse(req.defaultWarehouse());
        wh.setActive(req.active());
        DeliveryWarehouse saved = warehouseRepository.save(wh);
        auditLogService.logCurrentActor("DELIVERY_WAREHOUSE_SAVED", "DELIVERY_WAREHOUSE", saved.getId(),
                "Saved warehouse " + saved.getCode() + " (" + saved.getCity() + ")");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DeliveryPinRule> listPinRules() {
        return pinRuleRepository.findAllByOrderByPinPatternAsc();
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public DeliveryPinRule savePinRule(PinRuleRequest req) {
        String pattern = req.pinPattern().trim().toUpperCase(Locale.ROOT);
        DeliveryPinRule rule = pinRuleRepository.findByPinPatternIgnoreCase(pattern)
                .orElseGet(DeliveryPinRule::new);
        rule.setPinPattern(pattern);
        rule.setZoneType(req.zoneType());
        rule.setCourierName(req.courierName() != null && !req.courierName().isBlank() ? req.courierName().trim() : null);
        rule.setServiceable(req.serviceable() && req.zoneType() != DeliveryZoneType.UNSERVICEABLE);
        rule.setMinDaysOverride(req.minDaysOverride());
        rule.setMaxDaysOverride(req.maxDaysOverride());
        rule.setNotes(req.notes() != null && !req.notes().isBlank() ? req.notes().trim() : null);
        DeliveryPinRule saved = pinRuleRepository.save(rule);
        auditLogService.logCurrentActor("DELIVERY_PIN_RULE_SAVED", "DELIVERY_PIN_RULE", saved.getId(),
                "Saved PIN rule " + saved.getPinPattern() + " -> " + saved.getZoneType());
        return saved;
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public void deletePinRule(Long id) {
        DeliveryPinRule rule = pinRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryPinRule", id));
        pinRuleRepository.delete(rule);
        auditLogService.logCurrentActor("DELIVERY_PIN_RULE_DELETED", "DELIVERY_PIN_RULE", id,
                "Deleted PIN rule " + rule.getPinPattern());
    }

    @Transactional(readOnly = true)
    public List<DeliveryHoliday> listHolidays() {
        return holidayRepository.findAllByOrderByHolidayDateAsc();
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public DeliveryHoliday saveHoliday(HolidayRequest req) {
        DeliveryHoliday holiday = holidayRepository.findByHolidayDate(req.holidayDate())
                .orElseGet(DeliveryHoliday::new);
        holiday.setHolidayDate(req.holidayDate());
        holiday.setName(req.name().trim());
        DeliveryHoliday saved = holidayRepository.save(holiday);
        auditLogService.logCurrentActor("DELIVERY_HOLIDAY_SAVED", "DELIVERY_HOLIDAY", saved.getId(),
                "Saved holiday " + saved.getHolidayDate() + " (" + saved.getName() + ")");
        return saved;
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public void deleteHoliday(Long id) {
        DeliveryHoliday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryHoliday", id));
        holidayRepository.delete(holiday);
        auditLogService.logCurrentActor("DELIVERY_HOLIDAY_DELETED", "DELIVERY_HOLIDAY", id,
                "Deleted holiday " + holiday.getHolidayDate());
    }

    @Transactional(readOnly = true)
    public List<ProductDeliveryConfigDto> listProductDeliveryConfigs() {
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> !p.isDeleted())
                .sorted(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<Long, ProductDeliveryConfig> cfgMap = productConfigRepository.findAll().stream()
                .collect(Collectors.toMap(ProductDeliveryConfig::getProductId, c -> c));
        DeliveryWarehouse defaultWh = resolveWarehouse(null);

        List<ProductDeliveryConfigDto> out = new ArrayList<>();
        for (Product p : products) {
            ProductDeliveryConfig c = cfgMap.get(p.getId());
            DeliveryWarehouse wh = (c != null && c.getWarehouse() != null) ? c.getWarehouse() : defaultWh;
            out.add(new ProductDeliveryConfigDto(
                    p.getId(),
                    p.getName(),
                    p.getBrand() != null ? p.getBrand().getName() : "",
                    p.getSku(),
                    wh != null ? wh.getId() : null,
                    wh != null ? wh.getName() : "Default Vault",
                    c != null && c.getFulfillmentType() != null ? c.getFulfillmentType() : FulfillmentType.IN_STOCK,
                    c != null ? c.getProcessingDaysMin() : null,
                    c != null ? c.getProcessingDaysMax() : null,
                    c != null ? c.getShippingDaysMinOverride() : null,
                    c != null ? c.getShippingDaysMaxOverride() : null,
                    c != null ? c.getPreorderReleaseDate() : null,
                    c != null ? c.getSpecialOrderNote() : null
            ));
        }
        return out;
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public ProductDeliveryConfigDto saveProductDeliveryConfig(Long productId, ProductDeliveryConfigRequest req) {
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        ProductDeliveryConfig cfg = productConfigRepository.findById(productId)
                .orElseGet(() -> ProductDeliveryConfig.builder().product(product).build());

        DeliveryWarehouse wh = null;
        if (req.warehouseId() != null) {
            wh = warehouseRepository.findById(req.warehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", req.warehouseId()));
        }
        cfg.setWarehouse(wh);
        cfg.setFulfillmentType(req.fulfillmentType() != null ? req.fulfillmentType() : FulfillmentType.IN_STOCK);
        cfg.setProcessingDaysMin(req.processingDaysMin());
        cfg.setProcessingDaysMax(req.processingDaysMax());
        cfg.setShippingDaysMinOverride(req.shippingDaysMinOverride());
        cfg.setShippingDaysMaxOverride(req.shippingDaysMaxOverride());
        cfg.setPreorderReleaseDate(req.preorderReleaseDate());
        cfg.setSpecialOrderNote(req.specialOrderNote() != null && !req.specialOrderNote().isBlank()
                ? req.specialOrderNote().trim() : null);

        ProductDeliveryConfig saved = productConfigRepository.save(cfg);
        auditLogService.logCurrentActor("PRODUCT_DELIVERY_CONFIG_UPDATED", "PRODUCT", productId,
                "Updated delivery config for " + product.getSku() + " (" + saved.getFulfillmentType() + ")");

        DeliveryWarehouse effectiveWh = saved.getWarehouse() != null ? saved.getWarehouse() : resolveWarehouse(null);
        return new ProductDeliveryConfigDto(
                product.getId(),
                product.getName(),
                product.getBrand() != null ? product.getBrand().getName() : "",
                product.getSku(),
                effectiveWh != null ? effectiveWh.getId() : null,
                effectiveWh != null ? effectiveWh.getName() : "Default Vault",
                saved.getFulfillmentType(),
                saved.getProcessingDaysMin(),
                saved.getProcessingDaysMax(),
                saved.getShippingDaysMinOverride(),
                saved.getShippingDaysMaxOverride(),
                saved.getPreorderReleaseDate(),
                saved.getSpecialOrderNote()
        );
    }

    @CacheEvict(value = {"productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public void updateGlobalDeliverySettings(Map<String, String> params) {
        Map<String, String> filtered = new LinkedHashMap<>();
        params.forEach((k, v) -> {
            if (k != null && k.startsWith("delivery.") && v != null) {
                filtered.put(k, v.trim());
            }
        });
        if (!filtered.isEmpty()) {
            storeConfigService.updateSettings(filtered);
        }
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private String normalizeAndValidatePin(String rawPin) {
        if (rawPin == null || rawPin.isBlank()) {
            return null;
        }
        String digits = rawPin.trim().replaceAll("\\s+", "");
        if (!INDIAN_PIN_PATTERN.matcher(digits).matches()) {
            throw BusinessException.badRequest("INVALID_PIN_CODE",
                    "Please enter a valid 6-digit Indian PIN code (e.g. 560001)");
        }
        return digits;
    }

    private DeliveryWarehouse resolveWarehouse(ProductDeliveryConfig cfg) {
        if (cfg != null && cfg.getWarehouse() != null && cfg.getWarehouse().isActive()) {
            return cfg.getWarehouse();
        }
        return warehouseRepository.findFirstByDefaultWarehouseTrueAndActiveTrue()
                .or(() -> warehouseRepository.findFirstByActiveTrueOrderByIdAsc())
                .orElse(null);
    }

    private DeliveryPinRule findMatchingPinRule(String pinCode) {
        List<DeliveryPinRule> allRules = pinRuleRepository.findAllByOrderByPinPatternAsc();
        DeliveryPinRule bestPrefix = null;
        int bestPrefixLen = -1;

        for (DeliveryPinRule rule : allRules) {
            String pat = rule.getPinPattern().trim();
            if (pat.equalsIgnoreCase(pinCode)) {
                return rule; // Exact 6-digit match wins immediately
            }
            if (pat.endsWith("*")) {
                String prefix = pat.substring(0, pat.length() - 1);
                if (!prefix.isEmpty() && pinCode.startsWith(prefix) && prefix.length() > bestPrefixLen) {
                    bestPrefix = rule;
                    bestPrefixLen = prefix.length();
                }
            }
        }
        return bestPrefix;
    }

    private DeliveryZoneType classifyPinAgainstWarehouse(String pinCode, DeliveryWarehouse warehouse) {
        if (warehouse != null) {
            if (matchesAnyPrefix(pinCode, warehouse.getPinPrefixLocal())
                    || (warehouse.getPinCode() != null && warehouse.getPinCode().length() >= 3
                    && pinCode.startsWith(warehouse.getPinCode().substring(0, 3)))) {
                return DeliveryZoneType.LOCAL;
            }
            if (matchesAnyPrefix(pinCode, warehouse.getPinPrefixState())
                    || (warehouse.getPinCode() != null && warehouse.getPinCode().length() >= 2
                    && pinCode.startsWith(warehouse.getPinCode().substring(0, 2)))) {
                return DeliveryZoneType.SAME_STATE;
            }
        }
        if (pinCode.startsWith("79") || pinCode.startsWith("744") || pinCode.startsWith("194")) {
            return DeliveryZoneType.REMOTE;
        }
        return DeliveryZoneType.OTHER_STATE;
    }

    private boolean matchesAnyPrefix(String pinCode, String csvPrefixes) {
        if (csvPrefixes == null || csvPrefixes.isBlank()) {
            return false;
        }
        for (String token : csvPrefixes.split(",")) {
            String p = token.trim();
            if (!p.isEmpty() && pinCode.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private int[] getZoneDays(DeliveryZoneType zone) {
        return switch (zone) {
            case LOCAL -> new int[]{
                    storeConfigService.getInt("delivery.local_min_days", 1),
                    storeConfigService.getInt("delivery.local_max_days", 3)
            };
            case SAME_STATE -> new int[]{
                    storeConfigService.getInt("delivery.same_state_min_days", 2),
                    storeConfigService.getInt("delivery.same_state_max_days", 5)
            };
            case REMOTE -> new int[]{
                    storeConfigService.getInt("delivery.remote_min_days", 5),
                    storeConfigService.getInt("delivery.remote_max_days", 10)
            };
            default -> new int[]{
                    storeConfigService.getInt("delivery.other_state_min_days", 3),
                    storeConfigService.getInt("delivery.other_state_max_days", 7)
            };
        };
    }

    private static String formatZoneLabel(DeliveryZoneType zone) {
        return switch (zone) {
            case LOCAL -> "Local city delivery";
            case SAME_STATE -> "Same-state delivery";
            case REMOTE -> "Remote region delivery";
            case OTHER_STATE -> "Interstate insured delivery";
            default -> "Standard insured delivery";
        };
    }

    public static LocalDate nextBusinessDay(LocalDate date, boolean excludeWeekends, Set<LocalDate> holidays) {
        LocalDate cur = date;
        while (isNonBusinessDay(cur, excludeWeekends, holidays)) {
            cur = cur.plusDays(1);
        }
        return cur;
    }

    public static LocalDate addBusinessDays(LocalDate start, int businessDays, boolean excludeWeekends, Set<LocalDate> holidays) {
        LocalDate cur = nextBusinessDay(start, excludeWeekends, holidays);
        int added = 0;
        while (added < businessDays) {
            cur = cur.plusDays(1);
            if (!isNonBusinessDay(cur, excludeWeekends, holidays)) {
                added++;
            }
        }
        return cur;
    }

    private static boolean isNonBusinessDay(LocalDate date, boolean excludeWeekends, Set<LocalDate> holidays) {
        if (holidays != null && holidays.contains(date)) {
            return true;
        }
        if (excludeWeekends) {
            DayOfWeek dow = date.getDayOfWeek();
            return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        }
        return false;
    }
}
