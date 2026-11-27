package com.timecraft.delivery.repository;

import com.timecraft.delivery.entity.DeliveryHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeliveryHolidayRepository extends JpaRepository<DeliveryHoliday, Long> {
    List<DeliveryHoliday> findAllByOrderByHolidayDateAsc();
    Optional<DeliveryHoliday> findByHolidayDate(LocalDate holidayDate);
}
