package com.a501.service.domain.account.repository;

import java.util.Optional;
import com.a501.service.domain.account.entity.AccountVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AccountVerificationRepository extends JpaRepository<AccountVerification, Integer> {
    Optional<AccountVerification> findFirstByUserIdAndAccountFingerprintAndConfirmedAccountIdIsNullOrderByIdDesc(
            Integer userId, String accountFingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountVerification> findWithLockById(Integer id);
}
