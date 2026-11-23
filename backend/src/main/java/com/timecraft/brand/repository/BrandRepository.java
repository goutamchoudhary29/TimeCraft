package com.timecraft.brand.repository;

import com.timecraft.brand.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    List<Brand> findByDeletedFalseOrderByNameAsc();

    List<Brand> findByActiveTrueAndDeletedFalseOrderByNameAsc();

    List<Brand> findByFeaturedTrueAndActiveTrueAndDeletedFalseOrderByNameAsc();

    Optional<Brand> findBySlugAndDeletedFalse(String slug);

    Optional<Brand> findByIdAndDeletedFalse(Long id);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);
}
