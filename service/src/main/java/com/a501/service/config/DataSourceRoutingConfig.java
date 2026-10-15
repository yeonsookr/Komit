package com.a501.service.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * readOnly 트랜잭션은 읽기 DB(Standby)로, 나머지는 쓰기 DB(Primary)로 보냅니다.
 * app.datasource.read-url이 없으면 쓰기 DB 주소를 그대로 씁니다.
 *
 * 접속 정보는 Spring Boot의 {@link JdbcConnectionDetails}를 우선 씁니다. 운영에서는 spring.datasource.* 값이
 * 그대로 들어오고, 테스트에서는 Testcontainers {@code @ServiceConnection}이 만든 컨테이너 주소가 들어옵니다.
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceRoutingConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource writeDataSource(DataSourceProperties properties, ObjectProvider<JdbcConnectionDetails> details) {
        HikariDataSource dataSource = builder(properties, details).build();
        dataSource.setPoolName("write-pool");
        return dataSource;
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource readDataSource(
            DataSourceProperties properties,
            ObjectProvider<JdbcConnectionDetails> details,
            @Value("${app.datasource.read-url:}") String readUrl) {
        DataSourceBuilder<HikariDataSource> builder = builder(properties, details);
        if (!readUrl.isBlank()) {
            builder.url(readUrl);
        }
        HikariDataSource dataSource = builder.build();
        dataSource.setPoolName("read-pool");
        dataSource.setReadOnly(true);
        return dataSource;
    }

    // 실제 커넥션을 첫 SQL 시점까지 미뤄, 트랜잭션의 readOnly 여부가 정해진 뒤 풀을 고릅니다.
    @Bean
    @Primary
    DataSource dataSource(
            @Qualifier("writeDataSource") DataSource writeDataSource,
            @Qualifier("readDataSource") DataSource readDataSource) {
        LazyConnectionDataSourceProxy proxy = new LazyConnectionDataSourceProxy(writeDataSource);
        proxy.setReadOnlyDataSource(readDataSource);
        return proxy;
    }

    /**
     * {@link JdbcConnectionDetails} 빈이 있으면 그 값으로, 없으면 spring.datasource.* 값으로 풀을 만듭니다.
     * 이 설정이 DataSource를 직접 정의하므로 Boot는 spring.datasource.* 를 ConnectionDetails로 감싸 주지 않습니다.
     */
    private static DataSourceBuilder<HikariDataSource> builder(
            DataSourceProperties properties, ObjectProvider<JdbcConnectionDetails> details) {
        JdbcConnectionDetails connectionDetails = details.getIfAvailable();
        if (connectionDetails == null) {
            return properties.initializeDataSourceBuilder().type(HikariDataSource.class);
        }
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(connectionDetails.getDriverClassName())
                .url(connectionDetails.getJdbcUrl())
                .username(connectionDetails.getUsername())
                .password(connectionDetails.getPassword());
    }
}
