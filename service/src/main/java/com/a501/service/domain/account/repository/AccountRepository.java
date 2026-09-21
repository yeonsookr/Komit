package com.a501.service.domain.account.repository;

import com.a501.service.domain.account.entity.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Integer> {
    Optional<Account> findFirstByUserIdAndDeletedFalseOrderByIdDesc(Integer userId);
}
