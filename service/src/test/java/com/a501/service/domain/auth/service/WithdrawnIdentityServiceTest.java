package com.a501.service.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.account.service.AccountRetentionService;
import com.a501.service.domain.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** R-ACC-06 식별 정보 보관 기간과 R-AUTH-05 재가입 제한 기간을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class WithdrawnIdentityServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final LocalDateTime NOW_UTC = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final Long KAKAO_ID = 123L;

    @Mock
    private UserService userService;
    @Mock
    private AccountRetentionService accountRetentionService;

    private WithdrawnIdentityService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawnIdentityService(userService, accountRetentionService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("재가입 판정은 탈퇴 뒤 30일이 지난 옛 계정만 찾아 사용자와 계좌 식별 정보를 함께 지운다")
    void releasesOnlyAfterThirtyDays() {
        when(userService.findWithdrawnBefore(KAKAO_ID, NOW_UTC.minusDays(30))).thenReturn(Optional.of(12));
        when(userService.anonymize(12)).thenReturn(true);

        service.releaseForResignup(KAKAO_ID);

        InOrder order = inOrder(userService, accountRetentionService);
        order.verify(userService).anonymize(12);
        order.verify(accountRetentionService).anonymize(12);
    }

    @Test
    @DisplayName("기간이 남았거나 옛 계정이 없으면 아무것도 지우지 않는다")
    void doesNothingWithinBlockPeriod() {
        when(userService.findWithdrawnBefore(KAKAO_ID, NOW_UTC.minusDays(30))).thenReturn(Optional.empty());

        service.releaseForResignup(KAKAO_ID);

        verify(userService, never()).anonymize(any());
        verify(accountRetentionService, never()).anonymize(any());
    }

    @Test
    @DisplayName("정리 대상은 보관 기간 30일을 기준으로 고른다")
    void findsExpiredByRetentionPeriod() {
        when(userService.findAnonymizationTargets(NOW_UTC.minusDays(30), 100)).thenReturn(List.of(12, 15));

        assertThat(service.findExpired(100)).containsExactly(12, 15);
    }

    @Test
    @DisplayName("다른 파드가 먼저 지운 사람이면 계좌를 다시 건드리지 않는다")
    void skipsAccountWhenUserAlreadyAnonymized() {
        when(userService.anonymize(12)).thenReturn(false);

        assertThat(service.anonymize(12)).isFalse();
        verify(accountRetentionService, never()).anonymize(any());
    }
}
