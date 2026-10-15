package com.a501.service.domain.account.repository;

import java.util.Optional;
import com.a501.service.domain.account.entity.AccountVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountVerificationRepository extends JpaRepository<AccountVerification, Integer> {
    Optional<AccountVerification> findFirstByUserIdAndAccountFingerprintAndConfirmedAccountIdIsNullOrderByIdDesc(
            Integer userId, String accountFingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountVerification> findWithLockById(Integer id);

    /**
     * 이 사용자의 1원 인증 기록을 지웁니다. 명의·번호·지문이 모두 비울 수 없는 칸이라 행째 지웁니다. (R-ACC-06)
     * 확정된 계좌는 {@code accounts}에 따로 있어 이 기록이 없어도 남습니다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AccountVerification v where v.userId = :userId")
    int deleteByUserId(@Param("userId") Integer userId);
}
