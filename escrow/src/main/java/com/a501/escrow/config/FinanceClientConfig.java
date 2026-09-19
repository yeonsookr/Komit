package com.a501.escrow.config;

import com.a501.escrow.integration.finance.FinanceClient;
import com.a501.escrow.integration.finance.UnavailableFinanceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class FinanceClientConfig {
    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean(FinanceClient.class)
    FinanceClient unavailableFinanceClient() {
        return new UnavailableFinanceClient();
    }
}
