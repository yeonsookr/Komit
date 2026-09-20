package com.a501.service.common.security;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.a501.service.domain.user.service.UserService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** 기존 컨트롤러 슬라이스의 인증 사용자는 가입 완료 상태로 둔다. */
@TestConfiguration(proxyBeanMethods = false)
class SignupSecurityTestConfig {
    @Bean
    UserService signupSecurityUserService() {
        UserService users = Mockito.mock(UserService.class);
        when(users.isActive(anyInt())).thenReturn(true);
        when(users.isSignupComplete(anyInt())).thenReturn(true);
        return users;
    }
}
