package com.a501.service.domain.account.service;

import com.a501.service.domain.account.entity.MemberAccountProvision;
import com.a501.service.domain.account.repository.AccountRepository;
import com.a501.service.domain.account.repository.AccountVerificationRepository;
import com.a501.service.domain.account.repository.MemberAccountProvisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 회원의 계좌 식별 정보 정리. 보관 기간이 지났는지는 부르는 쪽이 판단합니다. (R-ACC-06)
 *
 * <p>입출금 기록({@code transaction_logs})은 거래 관계 기록이라 그대로 두고, 그 기록이 가리키는 계좌에서 명의와 번호만 지웁니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountRetentionService {
    /** 지운 명의 자리에 들어가는 문구. 다른 화면이 탈퇴한 사람을 부르는 말과 같습니다. */
    static final String WITHDRAWN_HOLDER = "탈퇴한 사용자";

    private final AccountRepository accountRepository;
    private final MemberAccountProvisionRepository provisionRepository;
    private final AccountVerificationRepository verificationRepository;

    /** 이 사용자의 계좌 명의·번호, 금융망 사용자 키, 1원 인증 기록을 지웁니다. 여러 번 불러도 결과가 같습니다. */
    @Transactional
    public void anonymize(Integer userId) {
        accountRepository.findByUserId(userId).forEach(account -> account.anonymize(WITHDRAWN_HOLDER));
        provisionRepository.findWithLockByUserId(userId).ifPresent(MemberAccountProvision::anonymize);
        verificationRepository.deleteByUserId(userId);
    }
}
