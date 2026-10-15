package com.a501.service.domain.setting.repository;

import java.util.Optional;

import com.a501.service.domain.setting.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, Integer> {
    Optional<Setting> findByUserIdAndDeletedFalse(Integer userId);
}
