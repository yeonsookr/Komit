package com.a501.service.domain.auth.service;

import com.a501.service.domain.account.service.AccountRetentionService;
import com.a501.service.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 회원의 식별 정보를 보관 기간이 지나면 지우고, 그 뒤에 같은 카카오 계정이 새로 가입할 수 있게 합니다.
 *
 * <p>두 규칙이 걸립니다. 식별 정보는 탈퇴 뒤 보관 기간이 지나야 지우고(R-ACC-06), 같은 카카오 계정은 재가입 제한 기간 동안 받지 않으며
 * 식별 정보가 지워진 뒤에야 새 계정으로 받습니다(R-AUTH-05). 두 값은 지금 같지만 서로 다른 규칙이라 따로 둡니다. (AGENTS.md 2)
 *
 * <p>사용자와 계좌 두 도메인을 한 트랜잭션에서 지워야 해서, 둘에 이미 의존하는 인증 도메인에 둡니다. 계좌 도메인이 사용자 도메인을
 * 부르므로 사용자 도메인에 두면 순환이 생깁니다. (BACKEND_CONVENTIONS 7.1)
 */
@Service
@Transactional(readOnly = true)
public class WithdrawnIdentityService {
    /** R-AUTH-05: 탈퇴 후 같은 카카오 계정으로 다시 가입할 수 없는 기간. */
    static final int RE_SIGNUP_BLOCK_DAYS = 30;
    /** R-ACC-06: 탈퇴 회원의 식별 정보를 지우기 전까지 보관하는 기간. */
    static final int IDENTITY_RETENTION_DAYS = 30;

    private final UserService userService;
    private final AccountRetentionService accountRetentionService;
    private final Clock clock;

    @Autowired
    public WithdrawnIdentityService(UserService userService, AccountRetentionService accountRetentionService) {
        this(userService, accountRetentionService, Clock.systemUTC());
    }

    WithdrawnIdentityService(UserService userService, AccountRetentionService accountRetentionService, Clock clock) {
        this.userService = userService;
        this.accountRetentionService = accountRetentionService;
        this.clock = clock;
    }

    /**
     * 이 카카오 계정의 옛 탈퇴 계정이 재가입 제한과 보관 기간을 모두 지났으면 식별 정보를 지워 새 가입 자리를 비웁니다.
     * 로그인 직전에 불러, 정리 작업이 아직 돌지 않았어도 기간이 지난 사람은 바로 새로 가입하게 합니다. (R-AUTH-05)
     * 기간이 남았으면 아무것도 하지 않고, 로그인은 지금처럼 거절됩니다.
     */
    @Transactional
    public void releaseForResignup(Long kakaoId) {
        LocalDateTime cutoff = now().minusDays(Math.max(RE_SIGNUP_BLOCK_DAYS, IDENTITY_RETENTION_DAYS));
        userService.findWithdrawnBefore(kakaoId, cutoff).ifPresent(this::anonymizeInCurrentTransaction);
    }

    /** 보관 기간이 지났는데 아직 식별 정보가 남은 탈퇴 회원. 식별자 순으로 {@code limit}명까지입니다. (R-ACC-06) */
    public List<Integer> findExpired(int limit) {
        return userService.findAnonymizationTargets(now().minusDays(IDENTITY_RETENTION_DAYS), limit);
    }

    /**
     * 한 사람의 식별 정보를 사용자·계좌 양쪽에서 함께 지웁니다. 여러 파드가 같은 사람을 골라도 사용자 행 잠금 뒤 다시 보므로 한 번만 지웁니다.
     *
     * @return 이번에 지웠으면 참
     */
    @Transactional
    public boolean anonymize(Integer userId) {
        return anonymizeInCurrentTransaction(userId);
    }

    private boolean anonymizeInCurrentTransaction(Integer userId) {
        if (!userService.anonymize(userId)) {
            return false;
        }
        accountRetentionService.anonymize(userId);
        return true;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
