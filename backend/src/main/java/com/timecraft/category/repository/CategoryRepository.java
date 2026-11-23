package com.timecraft.category.repository;

import com.timecraft.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByDeletedFalseOrderByDisplayOrderAscNameAsc();

    List<Category> findByActiveTrueAndDeletedFalseOrderByDisplayOrderAscNameAsc();

    Optional<Category> findBySlugAndDeletedFalse(String slug);

    Optional<Category> findByIdAndDeletedFalse(Long id);

    boolean existsBySlug(String slug);
}
