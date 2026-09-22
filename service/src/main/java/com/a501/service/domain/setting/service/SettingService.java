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

    /** 개발자 프로필 생성과 같은 트랜잭션에서 판매자 약관 동의를 켭니다. 설정 행이 없으면 만들어서 켭니다. */
    @Transactional
    public void agreeSellerTerms(Integer userId) {
        Setting setting = settingRepository.findByUserIdAndDeletedFalse(userId)
                .orElseGet(() -> settingRepository.save(Setting.forNewUser(userId)));
        setting.agreeSellerTerms();
    }

    /** 마이페이지가 알림 수신 설정을 조회하는 창구입니다. */
    public boolean isNotificationEnabled(Integer userId) {
        return settingRepository.findByUserIdAndDeletedFalse(userId)
                .map(Setting::isNotificationEnabled)
                .orElseThrow(() -> new IllegalStateException("사용자 설정을 찾을 수 없습니다."));
    }
}
