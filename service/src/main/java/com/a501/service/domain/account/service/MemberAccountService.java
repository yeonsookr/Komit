package com.a501.service.domain.account.service;

import com.a501.service.domain.account.dto.response.MemberAccountResponse;
import com.a501.service.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 계좌 등록 화면의 「계좌 연동하기」가 쓰는 조회입니다. */
@Service
@RequiredArgsConstructor
public class MemberAccountService {
    private final UserService users;
    private final MemberAccountProvisioningService provisioning;

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
}
