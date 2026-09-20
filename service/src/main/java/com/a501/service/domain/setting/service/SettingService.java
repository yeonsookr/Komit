package com.a501.service.domain.setting.service;

import com.a501.service.domain.setting.entity.Setting;
import com.a501.service.domain.setting.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingService {
    private final SettingRepository settingRepository;

    /** 사용자 생성과 설정 생성을 한 트랜잭션에서 처리하되, 동의 값은 초기 false로 둡니다. (R-ACC-12) */
    @Transactional
    public void createForNewUser(Integer userId) {
        settingRepository.save(Setting.forNewUser(userId));
    }
}
