package com.a501.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
// 진짜 서블릿 컨테이너를 띄운다. MOCK 으로 두면 ServerContainer 가 없어 WebSocket 버퍼 설정
// (WebSocketConfig.webSocketContainer) 이 기동에 실패하고, 애초에 WebSocket 등록 자체를
// 확인할 수 없다.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ServiceApplicationTests {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @Container
    @ServiceConnection
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:8.0.30-noble");

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void contextLoads() {
    }

    /** 시드를 늘리면 이 개수도 같은 MR에서 함께 고칩니다. */
    @Test
    void seedsAllDesignedStatusCodes() {
        assertThat(count("system_enum_groups")).isEqualTo(27);
        assertThat(count("system_enums")).isEqualTo(91);

        String reportResolvedName = jdbcClient.sql("""
                        SELECT code_name
                        FROM system_enums
                        WHERE group_code = 'REPORT_STATUS' AND code_key = 'RESOLVED'
                        """)
                .query(String.class)
                .single();
        assertThat(reportResolvedName).isEqualTo("해결됨");

        // R-ACC-13: 회원 발급 계좌의 진행 상태.
        Integer memberAccountStatusCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM system_enums
                        WHERE group_code = 'MEMBER_ACCOUNT_STATUS'
                          AND code_key IN ('PENDING', 'ISSUED', 'FAILED')
                        """)
                .query(Integer.class)
                .single();
        assertThat(memberAccountStatusCount).isEqualTo(3);

        Integer koreanNotificationCodeCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM system_enums
                        WHERE group_code = 'NOTIFICATION_TYPE'
                          AND code_key IN ('공지', '착수알림', '승인알림', '예약알림톡')
                        """)
                .query(Integer.class)
                .single();
        assertThat(koreanNotificationCodeCount).isEqualTo(4);
    }

    private int count(String tableName) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + tableName)
                .query(Integer.class)
                .single();
    }
}
