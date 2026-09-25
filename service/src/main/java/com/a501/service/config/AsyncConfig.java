package com.a501.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} 를 켠다. 세션 요약처럼 요청이 기다리지 않는 일을 뒤에서 돌린다.
 *
 * <p>실행기는 따로 만들지 않고 Spring Boot 의 {@code applicationTaskExecutor} 를 쓴다. 실행기 빈을
 * 직접 두면 Boot 가 제 것을 만들지 않아, 그 실행기를 쓰던 다른 곳까지 바뀐다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {}
