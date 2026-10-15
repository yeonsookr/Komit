package com.a501.service.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.user.entity.User;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 탈퇴 회원 식별 정보 정리 대상 조회를 실제 PostgreSQL 18에서 검증합니다. (R-ACC-06, BACKEND_CONVENTIONS 19) */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class UserRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final LocalDateTime CUTOFF = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("기준 시각 이전에 탈퇴했고 아직 지우지 않은 사람만 식별자 순으로, 요청한 수만큼 고른다")
    void findsWithdrawnBeforeCutoffNotYetAnonymized() {
        userRepository.deleteAll();
        userRepository.saveAndFlush(user(100L));
        User onTheLine = userRepository.saveAndFlush(withdrawn(101L, CUTOFF));
        User older = userRepository.saveAndFlush(withdrawn(102L, CUTOFF.minusDays(5)));
        userRepository.saveAndFlush(withdrawn(103L, CUTOFF.plusSeconds(1)));
        User anonymized = withdrawn(104L, CUTOFF.minusDays(10));
        userRepository.saveAndFlush(anonymized);
        anonymized.anonymize("탈퇴한 사용자");
        userRepository.saveAndFlush(anonymized);

        assertThat(userRepository.findAnonymizationTargets(CUTOFF, PageRequest.of(0, 10)))
                .containsExactly(onTheLine.getId(), older.getId());
        assertThat(userRepository.findAnonymizationTargets(CUTOFF, PageRequest.of(0, 1)))
                .containsExactly(onTheLine.getId());
    }

    @Test
    @DisplayName("지운 계정의 카카오 회원번호는 비어 같은 카카오 계정이 새로 가입할 수 있다")
    void anonymizedKakaoIdIsFreed() {
        userRepository.deleteAll();
        User old = userRepository.saveAndFlush(withdrawn(200L, CUTOFF.minusDays(1)));
        old.anonymize("탈퇴한 사용자");
        userRepository.saveAndFlush(old);

        assertThat(userRepository.findByKakaoId(200L)).isEmpty();
        User reborn = userRepository.saveAndFlush(user(200L));
        assertThat(reborn.getId()).isNotEqualTo(old.getId());
        assertThat(userRepository.findByKakaoId(200L)).map(User::getId).contains(reborn.getId());
    }

    private static User user(Long kakaoId) {
        return User.register(kakaoId, "김미미", null, "mimi@kakao.com", true, true,
                NOW, "protected", NOW.plusSeconds(3600), NOW);
    }

    private static User withdrawn(Long kakaoId, LocalDateTime deletedAt) {
        User user = user(kakaoId);
        user.withdraw(deletedAt);
        return user;
    }
}
