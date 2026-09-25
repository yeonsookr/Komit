package com.a501.service.domain.room.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.room.entity.Room;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * 내가 든 방 조회를 실제 PostgreSQL 18에서 검증합니다. (BACKEND_CONVENTIONS 19 Repository 통합 테스트)
 *
 * <p>맡긴 프로젝트가 없는 개발자는 {@code projectIds}가 빈 목록으로 옵니다. 빈 {@code in} 절이 오류 없이 거짓이 되는지를 여기서 봅니다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class RoomRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired
    private RoomRepository roomRepository;

    private Room mineAsClient;
    private Room mineAsDeveloper;

    @BeforeEach
    void setUp() {
        roomRepository.deleteAll();
        mineAsClient = roomRepository.saveAndFlush(Room.openInquiry(8, 3, null));
        mineAsDeveloper = roomRepository.saveAndFlush(Room.openInquiry(21, 9, null));
        roomRepository.saveAndFlush(Room.openInquiry(30, 5, null));
    }

    @Test
    @DisplayName("맡긴 프로젝트가 없는 개발자는 빈 목록을 넘겨도 개발자로 든 방만 받는다")
    void developerWithoutProjects() {
        assertThat(roomRepository.findMine(9, List.of(), null, null))
                .extracting(Room::getId)
                .containsExactly(mineAsDeveloper.getId());
    }

    @Test
    @DisplayName("개발자가 아닌 의뢰인은 맡긴 프로젝트의 방만 받는다")
    void clientWithoutDeveloperProfile() {
        assertThat(roomRepository.findMine(null, List.of(8), null, null))
                .extracting(Room::getId)
                .containsExactly(mineAsClient.getId());
    }

    @Test
    @DisplayName("두 자리를 모두 가진 사람은 두 쪽 방을 함께 받는다")
    void bothRoles() {
        assertThat(roomRepository.findMine(9, List.of(8), null, null))
                .extracting(Room::getId)
                .containsExactlyInAnyOrder(mineAsClient.getId(), mineAsDeveloper.getId());
    }
}
