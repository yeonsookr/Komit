package com.a501.service.config;

import com.a501.service.common.security.JwtAuthenticationFilter;
import com.a501.service.common.security.SecurityErrorWriter;
import com.a501.service.common.security.SignupAccessFilter;
import com.a501.service.common.security.TranscriberTokenFilter;
import com.a501.service.domain.user.service.UserService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    /**
     * 전사 경로만 보는 체인. 사용자 체인보다 먼저 봅니다.
     *
     * <p>노트북이 전사 토큰으로 부르는 경로라 사용자 JWT 와 섞지 않습니다. 사용자 토큰으로는 이
     * 경로가 열리지 않고, 전사 토큰으로는 다른 경로가 열리지 않습니다. (`job-contract.md` 11.2)
     */
    @Bean
    @Order(1)
    SecurityFilterChain transcriberFilterChain(
            HttpSecurity http,
            TranscriberProperties transcriberProperties,
            SecurityErrorWriter errorWriter) throws Exception {
        http
                .securityMatcher(TranscriberTokenFilter.PATH_PREFIX + "**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.unauthorized(response))
                        .accessDeniedHandler((request, response, exception) -> errorWriter.forbidden(response)))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(
                        new TranscriberTokenFilter(transcriberProperties, errorWriter),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            SecurityErrorWriter errorWriter,
            UserService userService) throws Exception {
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
                        // 브라우저는 네이티브 WebSocket 에 Authorization 헤더를 붙일 수 없습니다.
                        // 여기서 열어 두고 RealtimeHandshakeInterceptor 의 일회용 티켓이 관문 역할을 합니다.
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new SignupAccessFilter(userService, errorWriter), JwtAuthenticationFilter.class);
        return http.build();
    }
}
