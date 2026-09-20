package com.a501.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 를 켠다. pod 마다 돌며, 각자 자기에게 붙은 연결만 다룬다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
