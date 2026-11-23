package com.timecraft.authentication.repository;

import com.timecraft.authentication.entity.AccountStatus;
import com.timecraft.authentication.entity.RoleName;
import com.timecraft.authentication.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            SELECT DISTINCT u FROM User u
            JOIN u.roles r
            WHERE r.name = :roleName
            AND (:search IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
            AND (:status IS NULL OR u.accountStatus = :status)
            """)
    Page<User> searchByRole(
            @Param("roleName") RoleName roleName,
            @Param("search") String search,
            @Param("status") AccountStatus status,
            Pageable pageable
    );

    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.name = :roleName")
    long countByRoleName(@Param("roleName") RoleName roleName);

    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.createdAt >= :since")
    long countByRoleNameAndCreatedAtAfter(@Param("roleName") RoleName roleName, @Param("since") Instant since);
}
