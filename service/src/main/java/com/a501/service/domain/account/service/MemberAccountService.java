package com.a501.service.domain.account.service;

import com.a501.service.domain.account.dto.DepositAccount;
import com.a501.service.domain.account.dto.response.MemberAccountResponse;
import com.a501.service.domain.account.entity.MemberAccountProvision;
import com.a501.service.domain.account.repository.MemberAccountProvisionRepository;
import com.a501.service.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 계좌 등록 화면의 「계좌 연동하기」가 쓰는 조회입니다. */
@Service
@RequiredArgsConstructor
public class MemberAccountService {
    private final UserService users;
    private final MemberAccountProvisioningService provisioning;
    private final MemberAccountProvisionRepository provisions;
    private final AccountQueryService accounts;

    /**
     * 로그인한 본인에게 발급된 계좌를 돌려줍니다. 아직 없으면 이때 발급합니다.
     *
     * <p>대상 회원을 요청에서 받지 않고 토큰의 주인만 씁니다. 남의 번호를 가리킬 자리가
     * 없어야 하기 때문입니다. (R-ACC-14)
     */
    public MemberAccountResponse issue(Integer userId) {
        users.getActiveUser(userId);
        MemberAccountProvisioningService.MemberAccount account = provisioning.provision(userId);
        return new MemberAccountResponse(account.bankCode(), account.accountNo());
    }

    /**
     * 예치에서 돈이 빠지는 계좌. 회원가입 때 발급한 계좌이고, 아직 없으면 이때 발급합니다. (R-PAY-09)
     *
     * <p>화면에 보여 줄 값만 돌려줍니다. 계좌번호는 끝 네 자리만 남깁니다.
     */
    public DepositAccount depositAccount(Integer userId) {
        users.getActiveUser(userId);
        MemberAccountProvisioningService.MemberAccount account = provisioning.provision(userId);
        Long provisionId = provisions.findByUserId(userId)
                .map(MemberAccountProvision::getId)
                .orElseThrow(() -> new IllegalStateException("발급한 계좌의 행을 찾을 수 없습니다."));
        return new DepositAccount(provisionId, masked(accounts.bankNameOf(account.bankCode()), account.accountNo()));
    }

    private static String masked(String bankName, String accountNo) {
        String tail = "···· " + accountNo.substring(Math.max(0, accountNo.length() - 4));
        return bankName == null ? tail : bankName + " " + tail;
    }
}
