package com.a501.service.config;

import com.a501.service.common.security.JwtAuthenticationFilter;
import com.a501.service.common.security.SecurityErrorWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            SecurityErrorWriter errorWriter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.unauthorized(response))
                        .accessDeniedHandler((request, response, exception) -> errorWriter.forbidden(response)))
                .authorizeHttpRequests(authorize -> authorize
                        // 컨테이너가 sendError로 넘긴 오류 디스패치(/error)에 인증을 요구하면 모든 400·404가 401로 바뀝니다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // 로그인·재발급 경로는 메서드를 가리지 않고 공개해, 잘못된 메서드가 목과 같이 405로 나가게 합니다.
                        .requestMatchers("/api/auth/kakao", "/api/auth/token/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/landing/**",
                                "/api/codes/**",
                                "/api/developers",
                                "/api/developers/*",
                                "/api/developers/*/reviews").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/docs/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
