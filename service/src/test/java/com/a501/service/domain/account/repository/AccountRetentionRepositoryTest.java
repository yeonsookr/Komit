package com.a501.service.domain.account.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.account.entity.Account;
import com.a501.service.domain.account.entity.AccountVerification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 탈퇴 회원 계좌 정리가 쓰는 조회·삭제를 실제 PostgreSQL 18에서 검증합니다. (R-ACC-06, BACKEND_CONVENTIONS 19) */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class AccountRetentionRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AccountVerificationRepository verificationRepository;

    @Test
    @DisplayName("1원 인증 기록은 그 사용자 것만 지우고 남의 기록은 남긴다")
    void deletesOnlyThatUsersVerifications() {
        verificationRepository.deleteAll();
        verificationRepository.saveAndFlush(verification(12, "idem-1"));
        verificationRepository.saveAndFlush(verification(12, "idem-2"));
        AccountVerification others = verificationRepository.saveAndFlush(verification(13, "idem-3"));

        assertThat(verificationRepository.deleteByUserId(12)).isEqualTo(2);

        assertThat(verificationRepository.findAll())
                .extracting(AccountVerification::getId)
                .containsExactly(others.getId());
    }

    @Test
    @DisplayName("계좌는 그 사용자 것만 가져온다")
    void findsAccountsOfThatUser() {
        accountRepository.deleteAll();
        Account mine = accountRepository.saveAndFlush(Account.register(12, "김미미", "···· 4412", 58, "enc"));
        accountRepository.saveAndFlush(Account.register(13, "박준호", "···· 1234", 58, "enc"));

        assertThat(accountRepository.findByUserId(12))
                .extracting(Account::getId)
                .containsExactly(mine.getId());
    }

    private static AccountVerification verification(Integer userId, String idempotencyKey) {
        return AccountVerification.pending(userId, 58, "싸피은행", "김미미", "enc", "f".repeat(64), "···· 4412",
                idempotencyKey);
    }
}
