package com.timecraft.brand.service;

import com.timecraft.audit.service.AuditLogService;
import com.timecraft.brand.entity.Brand;
import com.timecraft.brand.repository.BrandRepository;
import com.timecraft.common.util.SlugUtils;
import com.timecraft.exception.BusinessException;
import com.timecraft.exception.ResourceNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final AuditLogService auditLogService;

    public record BrandRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 120) String slug,
            String description,
            @Size(max = 80) String countryOfOrigin,
            Integer foundedYear,
            @Size(max = 500) String logoUrl,
            @Size(max = 500) String bannerUrl,
            boolean featured,
            boolean active
    ) {
    }

    public record BrandDto(
            Long id,
            String name,
            String slug,
            String description,
            String countryOfOrigin,
            Integer foundedYear,
            String logoUrl,
            String bannerUrl,
            boolean featured,
            boolean active
    ) {
        public static BrandDto from(Brand b) {
            return new BrandDto(b.getId(), b.getName(), b.getSlug(), b.getDescription(),
                    b.getCountryOfOrigin(), b.getFoundedYear(), b.getLogoUrl(),
                    b.getBannerUrl(), b.isFeatured(), b.isActive());
        }
    }

    @Cacheable("brands")
    @Transactional(readOnly = true)
    public List<BrandDto> listActive() {
        return brandRepository.findByActiveTrueAndDeletedFalseOrderByNameAsc()
                .stream()
                .map(BrandDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BrandDto> listFeatured() {
        return brandRepository.findByFeaturedTrueAndActiveTrueAndDeletedFalseOrderByNameAsc()
                .stream()
                .map(BrandDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BrandDto> listAllAdmin() {
        return brandRepository.findByDeletedFalseOrderByNameAsc()
                .stream()
                .map(BrandDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BrandDto getBySlugOrId(String slugOrId) {
        Brand brand = findEntityBySlugOrId(slugOrId);
        return BrandDto.from(brand);
    }

    @Transactional(readOnly = true)
    public Brand requireEntity(Long id) {
        return brandRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", id));
    }

    @CacheEvict(value = {"brands", "productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public BrandDto create(BrandRequest req) {
        if (brandRepository.existsByNameIgnoreCase(req.name().trim())) {
            throw BusinessException.conflict("BRAND_EXISTS", "A brand with this name already exists");
        }
        String slug = resolveUniqueSlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : req.name(), null);
        Brand brand = new Brand();
        apply(brand, req, slug);
        brand = brandRepository.save(brand);
        auditLogService.logCurrentActor("BRAND_CREATED", "BRAND", brand.getId(), "Created brand " + brand.getName());
        return BrandDto.from(brand);
    }

    @CacheEvict(value = {"brands", "productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public BrandDto update(Long id, BrandRequest req) {
        Brand brand = requireEntity(id);
        String slug = resolveUniqueSlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : req.name(), id);
        apply(brand, req, slug);
        brand = brandRepository.save(brand);
        auditLogService.logCurrentActor("BRAND_UPDATED", "BRAND", brand.getId(), "Updated brand " + brand.getName());
        return BrandDto.from(brand);
    }

    @CacheEvict(value = {"brands", "productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        Brand brand = requireEntity(id);
        brand.setDeleted(true);
        brand.setActive(false);
        brandRepository.save(brand);
        auditLogService.logCurrentActor("BRAND_DELETED", "BRAND", id, "Soft-deleted brand " + brand.getName());
    }

    private Brand findEntityBySlugOrId(String slugOrId) {
        if (slugOrId.chars().allMatch(Character::isDigit)) {
            return requireEntity(Long.valueOf(slugOrId));
        }
        return brandRepository.findBySlugAndDeletedFalse(slugOrId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", slugOrId));
    }

    private String resolveUniqueSlug(String raw, Long currentId) {
        String base = SlugUtils.slugify(raw);
        if (base.isBlank()) {
            base = "brand";
        }
        String candidate = base;
        int suffix = 1;
        while (true) {
            var existing = brandRepository.findBySlugAndDeletedFalse(candidate);
            if (existing.isEmpty() || (currentId != null && existing.get().getId().equals(currentId))) {
                return candidate;
            }
            candidate = base + "-" + (++suffix);
        }
    }

    private void apply(Brand target, BrandRequest req, String slug) {
        target.setName(req.name().trim());
        target.setSlug(slug);
        target.setDescription(req.description());
        target.setCountryOfOrigin(req.countryOfOrigin());
        target.setFoundedYear(req.foundedYear());
        target.setLogoUrl(req.logoUrl());
        target.setBannerUrl(req.bannerUrl());
        target.setFeatured(req.featured());
        target.setActive(req.active());
    }
}
