package com.a501.service.domain.payment.service;

import com.a501.service.domain.account.dto.DepositAccount;
import com.a501.service.domain.account.service.MemberAccountService;
import com.a501.service.domain.payment.dto.response.PaymentMethodListResponse;
import com.a501.service.domain.payment.dto.response.PaymentMethodResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 예치 화면의 결제 수단.
 *
 * <p>예치는 회원가입 때 발급한 계좌에서만 빠지므로 그 계좌 하나를 기본 수단으로 내려준다.
 * 다른 계좌나 결제창은 없다. (`R-PAY-09`)
 */
@Service
@RequiredArgsConstructor
public class PaymentMethodService {
    private final MemberAccountService memberAccounts;

    public PaymentMethodListResponse findPaymentMethods(Integer userId) {
        DepositAccount account = memberAccounts.depositAccount(userId);
        return new PaymentMethodListResponse(List.of(new PaymentMethodResponse(
                Math.toIntExact(account.provisionId()),
                PaymentMethodResponse.MethodType.REALTIME_TRANSFER,
                account.maskedNumber(),
                true)));
    }
}
