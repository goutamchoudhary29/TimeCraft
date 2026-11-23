package com.timecraft.category.service;

import com.timecraft.audit.service.AuditLogService;
import com.timecraft.category.entity.Category;
import com.timecraft.category.repository.CategoryRepository;
import com.timecraft.common.util.SlugUtils;
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
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    public record CategoryRequest(
            Long parentId,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 120) String slug,
            String description,
            @Size(max = 500) String imageUrl,
            int displayOrder,
            boolean active
    ) {
    }

    public record CategoryDto(
            Long id,
            Long parentId,
            String name,
            String slug,
            String description,
            String imageUrl,
            int displayOrder,
            boolean active
    ) {
        public static CategoryDto from(Category c) {
            return new CategoryDto(
                    c.getId(),
                    c.getParent() != null ? c.getParent().getId() : null,
                    c.getName(),
                    c.getSlug(),
                    c.getDescription(),
                    c.getImageUrl(),
                    c.getDisplayOrder(),
                    c.isActive()
            );
        }
    }

    @Cacheable("categories")
    @Transactional(readOnly = true)
    public List<CategoryDto> listActive() {
        return categoryRepository.findByActiveTrueAndDeletedFalseOrderByDisplayOrderAscNameAsc()
                .stream()
                .map(CategoryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listAllAdmin() {
        return categoryRepository.findByDeletedFalseOrderByDisplayOrderAscNameAsc()
                .stream()
                .map(CategoryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryDto getBySlugOrId(String slugOrId) {
        if (slugOrId.chars().allMatch(Character::isDigit)) {
            return CategoryDto.from(requireEntity(Long.valueOf(slugOrId)));
        }
        return CategoryDto.from(categoryRepository.findBySlugAndDeletedFalse(slugOrId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", slugOrId)));
    }

    @Transactional(readOnly = true)
    public Category requireEntity(Long id) {
        return categoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @CacheEvict(value = {"categories", "productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public CategoryDto create(CategoryRequest req) {
        String slug = resolveUniqueSlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : req.name(), null);
        Category category = new Category();
        apply(category, req, slug);
        category = categoryRepository.save(category);
        auditLogService.logCurrentActor("CATEGORY_CREATED", "CATEGORY", category.getId(),
                "Created category " + category.getName());
        return CategoryDto.from(category);
    }

    @CacheEvict(value = {"categories", "productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public CategoryDto update(Long id, CategoryRequest req) {
        Category category = requireEntity(id);
        String slug = resolveUniqueSlug(req.slug() != null && !req.slug().isBlank() ? req.slug() : req.name(), id);
        apply(category, req, slug);
        category = categoryRepository.save(category);
        auditLogService.logCurrentActor("CATEGORY_UPDATED", "CATEGORY", category.getId(),
                "Updated category " + category.getName());
        return CategoryDto.from(category);
    }

    @CacheEvict(value = {"categories", "productDetails", "homeContent"}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        Category category = requireEntity(id);
        category.setDeleted(true);
        category.setActive(false);
        categoryRepository.save(category);
        auditLogService.logCurrentActor("CATEGORY_DELETED", "CATEGORY", id,
                "Soft-deleted category " + category.getName());
    }

    private String resolveUniqueSlug(String raw, Long currentId) {
        String base = SlugUtils.slugify(raw);
        if (base.isBlank()) {
            base = "category";
        }
        String candidate = base;
        int suffix = 1;
        while (true) {
            var existing = categoryRepository.findBySlugAndDeletedFalse(candidate);
            if (existing.isEmpty() || (currentId != null && existing.get().getId().equals(currentId))) {
                return candidate;
            }
            candidate = base + "-" + (++suffix);
        }
    }

    private void apply(Category target, CategoryRequest req, String slug) {
        target.setParent(req.parentId() != null ? requireEntity(req.parentId()) : null);
        target.setName(req.name().trim());
        target.setSlug(slug);
        target.setDescription(req.description());
        target.setImageUrl(req.imageUrl());
        target.setDisplayOrder(req.displayOrder());
        target.setActive(req.active());
    }
}
