package com.a501.service.domain.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.a501.service.domain.setting.entity.Setting;
import com.a501.service.domain.setting.repository.SettingRepository;
import com.a501.service.domain.user.dto.UserSnapshot;
import com.a501.service.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TermsAgreementServiceTest {
    @Mock
    private SettingRepository settingRepository;
    @Mock
    private UserService userService;

    private TermsAgreementService termsAgreementService;

    @BeforeEach
    void setUp() {
        termsAgreementService = new TermsAgreementService(settingRepository, userService);
    }

    @Test
    void newUserRemainsPendingUntilExplicitAgreement() {
        Setting setting = Setting.forNewUser(12);
        when(userService.getActiveUser(12)).thenReturn(new UserSnapshot(12, "김미미", null, false));
        when(settingRepository.findByUserIdAndDeletedFalse(12)).thenReturn(Optional.of(setting));

        assertThat(termsAgreementService.getRequiredTerms(12).isServiceTermsAgreed()).isFalse();
        assertThat(termsAgreementService.agreeRequiredTerms(12).isServiceTermsAgreed()).isTrue();
        assertThat(setting.isSellerTermsAgreed()).isFalse();
        assertThat(termsAgreementService.agreeRequiredTerms(12).isEscrowTermsAgreed()).isTrue();
    }
}
