package com.a501.service.common.security;

import java.io.IOException;
import java.util.List;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final SecurityErrorWriter errorWriter;

    /**
     * 전사 경로는 보지 않습니다. 그 경로의 {@code Bearer} 는 JWT 가 아니라 전사 토큰이라, 여기서
     * 검증하면 전부 401 이 됩니다. 이 필터는 서블릿 필터로도 올라가 보안 체인 밖에서도 돕니다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return TranscriberTokenFilter.isTranscriberPath(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX) || authorization.length() == BEARER_PREFIX.length()) {
            errorWriter.unauthorized(response);
            return;
        }
        try {
            Integer userId = tokenProvider.verifyAndGetUserId(authorization.substring(BEARER_PREFIX.length()));
            AuthenticatedUser principal = new AuthenticatedUser(userId);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            filterChain.doFilter(request, response);
        } catch (JWTVerificationException exception) {
            SecurityContextHolder.clearContext();
            errorWriter.unauthorized(response);
        }
    }
}
