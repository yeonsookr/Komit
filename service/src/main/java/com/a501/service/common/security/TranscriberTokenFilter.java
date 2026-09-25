package com.a501.service.common.security;

import com.a501.service.config.TranscriberProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 전사 경로만 지키는 필터. 전사 토큰이 맞아야 지나갑니다. (`ai/worker/docs/job-contract.md` 11.2)
 *
 * <p><b>빈으로 등록하지 않습니다.</b> {@code @Component} 로 두면 Spring Boot 가 서블릿 필터로도
 * 올려 모든 요청에 걸립니다. 전사 보안 체인 안에서만 돌아야 합니다.
 */
public class TranscriberTokenFilter extends OncePerRequestFilter {
    /** 전사 경로. 사용자 JWT 필터는 이 아래를 보지 않습니다. */
    public static final String PATH_PREFIX = "/api/transcription-jobs/";

    private static final String BEARER_PREFIX = "Bearer ";

    private final TranscriberProperties properties;
    private final SecurityErrorWriter errorWriter;

    public TranscriberTokenFilter(TranscriberProperties properties, SecurityErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)
                || !properties.accepts(authorization.substring(BEARER_PREFIX.length()))) {
            errorWriter.unauthorized(response);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "transcriber", null, List.of(new SimpleGrantedAuthority("ROLE_TRANSCRIBER"))));
        filterChain.doFilter(request, response);
    }

    /** 이 요청이 전사 경로인지. */
    public static boolean isTranscriberPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + PATH_PREFIX);
    }
}
