package com.a501.service.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.domain.user.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

/** R-ACC-13: 가입 진행 중에는 가입 절차만 허용한다. */
class SignupAccessFilterTest {
    private static final Integer USER_ID = 5;

    private final UserService users = mock(UserService.class);
    private final SignupAccessFilter filter =
            new SignupAccessFilter(users, new SecurityErrorWriter(new ObjectMapper()));

    @BeforeEach
    void signedInWhileSigningUp() {
        when(users.isActive(USER_ID)).thenReturn(true);
        when(users.isSignupComplete(USER_ID)).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedUser(USER_ID), null, null));
    }

    @Test
    void letsSignupFinishItsAccountSteps() throws Exception {
        assertThat(passes("POST", "/api/accounts/member-account")).isTrue();
        assertThat(passes("POST", "/api/accounts/verifications")).isTrue();
    }

    @Test
    void stillBlocksTheRestOfTheService() throws Exception {
        assertThat(passes("GET", "/api/mypage")).isFalse();
        assertThat(passes("GET", "/api/accounts/member-account")).isFalse();
    }

    private boolean passes(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        return response.getStatus() == 200;
    }
}
