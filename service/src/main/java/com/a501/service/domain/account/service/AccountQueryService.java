package com.a501.service.domain.account.service;

import com.a501.service.domain.account.dto.response.AccountResponse;
import com.a501.service.domain.account.entity.Account;
import com.a501.service.domain.account.repository.AccountRepository;
import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.code.service.CodeQueryService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인이 계좌 엔티티나 원문 계좌번호 없이 정산 계좌 표시 정보만 가져가는 창구입니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryService {
    private final AccountRepository accountRepository;
    private final CodeQueryService codeQueryService;

    public AccountResponse findSettlementAccount(Integer userId) {
        return accountRepository.findFirstByUserIdAndDeletedFalseOrderByIdDesc(userId)
                .map(this::toResponse)
                .orElse(null);
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
