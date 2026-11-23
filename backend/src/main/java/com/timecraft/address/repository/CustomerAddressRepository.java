package com.timecraft.address.repository;

import com.timecraft.address.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(Long userId);

    Optional<CustomerAddress> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE CustomerAddress a SET a.defaultAddress = false WHERE a.user.id = :userId")
    void clearDefaultForUser(@Param("userId") Long userId);
}
