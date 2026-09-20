package com.a501.service.domain.setting.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.setting.dto.response.RequiredTermsResponse;
import com.a501.service.domain.setting.entity.Setting;
import com.a501.service.domain.setting.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequiredTermsWriter {
    private final SettingRepository settings;

    @Transactional
    public RequiredTermsResponse agree(Integer userId) {
        Setting setting = settings.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        setting.agreeRequiredTerms();
        return RequiredTermsResponse.from(setting);
    }
}
