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

    /**
     * 가입과 함께 필수 약관 동의를 기록합니다. 가입 트랜잭션 안에서 불리면 그 트랜잭션에 합류해
     * 사용자 생성과 설정 생성이 함께 커밋되거나 함께 롤백됩니다.
     */
    @Transactional
    public void createWithRequiredTerms(Integer userId) {
        settingRepository.save(Setting.agreeRequiredTerms(userId));
    }
}
