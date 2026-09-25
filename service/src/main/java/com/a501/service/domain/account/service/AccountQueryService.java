package com.a501.service.domain.account.service;

import com.a501.service.domain.account.crypto.AccountDataCrypto;
import com.a501.service.domain.account.dto.PayoutAccount;
import com.a501.service.domain.account.dto.response.AccountResponse;
import com.a501.service.domain.account.entity.Account;
import com.a501.service.domain.account.entity.MemberAccountProvision;
import com.a501.service.domain.account.repository.AccountRepository;
import com.a501.service.domain.account.repository.MemberAccountProvisionRepository;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.code.service.CodeQueryService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인이 계좌 엔티티나 원문 계좌번호 없이 정산 계좌 표시 정보만 가져가는 창구입니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryService {
    /** R-ACC-10: 계좌 등록 화면이 고를 수 있는 유일한 은행(싸피은행)의 {@code BANK_CODE} 코드 id. */
    private static final int SSAFY_BANK_CODE_ID = 58;

    /** R-ACC-10: 위 은행의 금융망 은행코드. */
    private static final String SSAFY_FINANCE_BANK_CODE = "001";

    private final AccountRepository accountRepository;
    private final MemberAccountProvisionRepository provisionRepository;
    private final AccountDataCrypto crypto;
    private final CodeQueryService codeQueryService;

    public AccountResponse findSettlementAccount(Integer userId) {
        return accountRepository.findFirstByUserIdAndDeletedFalseOrderByIdDesc(userId)
                .map(this::toResponse)
                .orElse(null);
    }

    /**
     * 대금을 받을 계좌. 마이페이지에 등록한 계좌입니다. (`R-PAY-14`)
     *
     * <p><b>예금주명은 등록 화면에 적은 이름이 아닙니다.</b> Escrow 는 지급 전에 금융망 예금주를 조회해
     * 글자까지 같아야 보냅니다. 등록 계좌는 1원 인증을 거쳤고, 1원 인증은 그 회원의 금융망 키로 조회되는
     * 본인 계좌에만 통과하므로 금융망 예금주는 그 회원입니다. 금융망은 회원 ID(이메일 형식)의 {@code @}
     * 앞부분을 예금주명으로 씁니다.
     *
     * <p>다음이면 비어 있습니다 — 등록 계좌가 없을 때, 원문 번호가 없을 때, 금융망에 없는 은행일 때,
     * 회원 금융 계정이 없을 때. 묻는 쪽은 지급을 미루고 다음에 다시 봅니다.
     *
     * @param userId 대금을 받을 사람
     */
    public Optional<PayoutAccount> findPayoutAccount(Integer userId) {
        Optional<Account> account = accountRepository.findFirstByUserIdAndDeletedFalseOrderByIdDesc(userId)
                .filter(found -> found.getAccountNoEncrypted() != null)
                .filter(found -> found.getBankCodeId() == SSAFY_BANK_CODE_ID);
        if (account.isEmpty()) {
            return Optional.empty();
        }
        return provisionRepository.findByUserId(userId)
                .map(MemberAccountProvision::getEscrowUserId)
                .map(AccountQueryService::holderNameOf)
                .map(holder -> new PayoutAccount(
                        SSAFY_FINANCE_BANK_CODE,
                        crypto.decrypt(account.get().getAccountNoEncrypted()),
                        holder));
    }

    /**
     * 예치금을 돌려받을 계좌. 회원가입 때 발급한 계좌입니다. (`R-PAY-04`)
     *
     * <p>등록 계좌(`R-PAY-14`)가 아닙니다. 예치는 발급 계좌에서 빠지고(`R-PAY-09`), Escrow 는 환불 계좌가 예치
     * 출금 계좌와 같아야 보냅니다. 예금주명은 지급과 같은 방식으로 금융망 사용자 ID 에서 뗍니다.
     *
     * <p>발급이 끝나지 않았으면 비어 있습니다. 발급 전에는 예치할 수도 없었으므로 돌려줄 돈도 없습니다.
     *
     * @param userId 돈을 돌려받을 의뢰인
     */
    public Optional<PayoutAccount> findRefundAccount(Integer userId) {
        return provisionRepository.findByUserId(userId)
                .filter(MemberAccountProvision::isComplete)
                .map(provision -> new PayoutAccount(
                        provision.getBankCode(),
                        crypto.decrypt(provision.getAccountNoEncrypted()),
                        holderNameOf(provision.getEscrowUserId())));
    }

    /**
     * 금융망 사용자 ID 에서 예금주명을 뗍니다. {@code komit-5@komit.local} → {@code komit-5}
     *
     * <p>명세는 예금주명을 10자로 적지만 실제 금융망은 자르지 않습니다. 길이 상한은 Escrow 의
     * {@code recipientName} 20자입니다. (`escrow/docs/FINANCE_MAPPING.md` 4절, 2026-09-25 실측)
     */
    private static String holderNameOf(String financeUserId) {
        int at = financeUserId.indexOf('@');
        return at < 0 ? financeUserId : financeUserId.substring(0, at);
    }

    private AccountResponse toResponse(Account account) {
        Map<Integer, CodeResponse> codes = codeQueryService.findCodes(List.of(account.getBankCodeId()));
        CodeResponse bank = codes.get(account.getBankCodeId());
        if (bank == null) {
            throw new IllegalStateException("등록 계좌의 은행 코드를 찾을 수 없습니다.");
        }
        return new AccountResponse(
                account.getId(),
                bank.codeName(),
                account.getMaskedNumber(),
                account.getAccountHolder());
    }
}
