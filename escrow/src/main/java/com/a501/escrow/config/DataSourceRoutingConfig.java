package com.a501.escrow.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * readOnly 트랜잭션은 읽기 DB(Standby)로, 나머지는 쓰기 DB(Primary)로 보냅니다.
 * app.datasource.read-url이 없으면 쓰기 DB 주소를 그대로 씁니다.
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceRoutingConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource writeDataSource(DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("write-pool");
        return dataSource;
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource readDataSource(
            DataSourceProperties properties,
            @Value("${app.datasource.read-url:${spring.datasource.url}}") String readUrl) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .url(readUrl)
                .build();
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
}
