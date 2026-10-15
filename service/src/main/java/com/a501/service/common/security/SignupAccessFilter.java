package com.a501.service.common.security;

import java.io.IOException;

import com.a501.service.domain.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 가입 진행 중에는 가입 절차만 허용한다. (R-ACC-13) */
public class SignupAccessFilter extends OncePerRequestFilter {
    private final UserService users;
    private final SecurityErrorWriter errors;

    public SignupAccessFilter(UserService users, SecurityErrorWriter errors) {
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            chain.doFilter(request, response);
            return;
        }
        if (!users.isActive(user.userId())) {
            errors.unauthorized(response);
            return;
        }
        if (!users.isSignupComplete(user.userId()) && !isSignupPath(request)) {
            errors.forbidden(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isSignupPath(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        if (path.equals("/api/settings/terms") || path.equals("/api/auth/signup/completion")
                || path.equals("/api/auth/logout")) return true;
        if (HttpMethod.GET.matches(method) && path.startsWith("/api/codes/")) return true;
        if (!HttpMethod.POST.matches(method)) return false;
        // 계좌 연동과 1원 인증은 가입 절차의 일부다. (R-ACC-13, R-ACC-14)
        return path.equals("/api/accounts/member-account")
                || path.startsWith("/api/accounts/verifications");
    }
}
