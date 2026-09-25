package com.a501.service.domain.account.repository;

import com.a501.service.domain.account.entity.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Integer> {
    Optional<Account> findFirstByUserIdAndDeletedFalseOrderByIdDesc(Integer userId);

    /** 이 사용자의 계좌 전부. 바꿔 끼워 지운 표시가 된 계좌도 듭니다. 탈퇴 뒤 명의를 지울 때 씁니다. (R-ACC-06) */
    List<Account> findByUserId(Integer userId);
}
