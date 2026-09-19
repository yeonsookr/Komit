package com.a501.escrow.config;

import com.a501.escrow.integration.finance.FinanceClient;
import com.a501.escrow.integration.finance.UnavailableFinanceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FinanceClientConfig {
    @Bean
    @ConditionalOnMissingBean(FinanceClient.class)
    FinanceClient unavailableFinanceClient() {
        return new UnavailableFinanceClient();
    }
}
