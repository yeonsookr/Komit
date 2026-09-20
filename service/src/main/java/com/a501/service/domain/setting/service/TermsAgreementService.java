package com.a501.service.domain.setting.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.setting.dto.response.RequiredTermsResponse;
import com.a501.service.domain.setting.entity.Setting;
import com.a501.service.domain.setting.repository.SettingRepository;
import com.a501.service.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsAgreementService {
    private final SettingRepository settingRepository;
    private final UserService userService;
    private final RequiredTermsWriter writer;

    public RequiredTermsResponse getRequiredTerms(Integer userId) {
        return RequiredTermsResponse.from(getSettingOfActiveUser(userId));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RequiredTermsResponse agreeRequiredTerms(Integer userId) {
        userService.getActiveUser(userId);
        return writer.agree(userId);
    }

    private Setting getSettingOfActiveUser(Integer userId) {
        userService.getActiveUser(userId);
        return settingRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
