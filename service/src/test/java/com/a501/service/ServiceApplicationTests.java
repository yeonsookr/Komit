package com.a501.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class ServiceApplicationTests {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void contextLoads() {
    }

    @Test
    void flywaySeedsAllDesignedStatusCodes() {
        assertThat(count("system_enum_groups")).isEqualTo(26);
        assertThat(count("system_enums")).isEqualTo(87);

        String reportResolvedName = jdbcClient.sql("""
                        SELECT code_name
                        FROM system_enums
                        WHERE group_code = 'REPORT_STATUS' AND code_key = 'RESOLVED'
                        """)
                .query(String.class)
                .single();
        assertThat(reportResolvedName).isEqualTo("해결됨");

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
