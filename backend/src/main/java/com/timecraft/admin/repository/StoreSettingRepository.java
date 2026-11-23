package com.timecraft.admin.repository;

import com.timecraft.admin.entity.StoreSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreSettingRepository extends JpaRepository<StoreSetting, Long> {

    Optional<StoreSetting> findBySettingKey(String settingKey);

    List<StoreSetting> findAllByOrderBySettingGroupAscSettingKeyAsc();

    List<StoreSetting> findBySettingGroupOrderBySettingKeyAsc(String settingGroup);
}
