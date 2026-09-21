package com.a501.service.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.a501.service.config.AuthProperties;
import com.a501.service.config.SecurityConfig;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.TestPropertySource;

/**
 * 보안 체인까지 태우는 컨트롤러 슬라이스 테스트입니다.
 *
 * Spring Boot 4의 {@code @WebMvcTest}는 Spring Security 자동 설정을 포함하지 않으므로 여기서 직접 넣고,
 * 이 프로젝트의 {@link SecurityConfig}와 JWT 검증에 필요한 빈·설정값을 함께 올립니다.
 * 컨트롤러 테스트는 이 애너테이션 하나로 실제 인증·인가 규칙을 검증합니다. (BACKEND_CONVENTIONS 19)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@WebMvcTest
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import({SecurityConfig.class, JwtTokenProvider.class, SecurityErrorWriter.class,
        SignupSecurityTestConfig.class})
@EnableConfigurationProperties(AuthProperties.class)
@TestPropertySource(properties = {
        "app.auth.user-jwt-secret=" + SecuritySliceTestSupport.USER_JWT_SECRET,
        "app.auth.kakao-token-encryption-key=" + SecuritySliceTestSupport.KAKAO_TOKEN_ENCRYPTION_KEY
})
public @interface WebMvcSecurityTest {
    /** 테스트할 컨트롤러. {@code @WebMvcTest(controllers = ...)}와 같습니다. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
