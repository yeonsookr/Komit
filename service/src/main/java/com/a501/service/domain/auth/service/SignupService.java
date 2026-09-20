package com.a501.service.domain.auth.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.auth.SignupErrorCode;
import com.a501.service.domain.account.service.MemberAccountProvisioningService;
import com.a501.service.domain.setting.dto.response.RequiredTermsResponse;
import com.a501.service.domain.setting.service.TermsAgreementService;
import com.a501.service.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 외부 발급 요청을 DB 트랜잭션 밖에서 끝낸 뒤 가입 상태를 확정한다. */
@Service
@RequiredArgsConstructor
public class SignupService {
    private final UserService users;
    private final TermsAgreementService terms;
    private final MemberAccountProvisioningService provisioning;

    public void requireComplete(Integer userId) {
        users.getActiveUser(userId);
        if (!users.isSignupComplete(userId)) {
            throw new BusinessException(SignupErrorCode.SIGNUP_PENDING);
        }
    }

    public void complete(Integer userId) {
        users.getActiveUser(userId);
        if (users.isSignupComplete(userId)) return;

        RequiredTermsResponse agreement = terms.getRequiredTerms(userId);
        if (!agreement.isEscrowTermsAgreed() || !agreement.isPrivacyCollectAgreed()
                || !agreement.isPrivacyThirdPartyAgreed() || !agreement.isServiceTermsAgreed()) {
            throw new BusinessException(SignupErrorCode.TERMS_NOT_AGREED);
        }

        // R-ACC-09, R-ACC-13: 계좌 발급 확인 뒤에만 가입을 활성화한다.
        provisioning.ensureProvisioned(userId);
        users.completeSignup(userId);
    }
}
