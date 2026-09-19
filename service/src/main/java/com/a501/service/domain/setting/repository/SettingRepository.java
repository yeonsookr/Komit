package com.a501.service.domain.setting.repository;

import com.a501.service.domain.setting.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, Integer> {}
