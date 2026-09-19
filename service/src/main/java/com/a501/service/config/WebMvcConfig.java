package com.a501.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {
    /** 응답은 Accept 헤더와 무관하게 항상 JSON입니다. 목 서버(FastAPI)가 Accept를 보지 않으므로 406 대신 같은 JSON을 돌려줍니다. */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.ignoreAcceptHeader(true).defaultContentType(MediaType.APPLICATION_JSON);
    }
}
