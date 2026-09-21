package com.a501.service.domain.account.repository;

import java.util.Optional;
import com.a501.service.domain.account.entity.MemberAccountProvision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface MemberAccountProvisionRepository extends JpaRepository<MemberAccountProvision, Long> {
    Optional<MemberAccountProvision> findByUserId(Integer userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MemberAccountProvision> findWithLockByUserId(Integer userId);
}
