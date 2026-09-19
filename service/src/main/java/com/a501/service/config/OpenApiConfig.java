package com.a501.service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc 문서의 공통 정보. 실제 구현 문서({@code /docs})는 백엔드 내부 확인용이며, 팀의 계약 기준은 목 서버 Swagger입니다.
 * (BACKEND_CONVENTIONS 5.2, 17.2)
 *
 * <p>컨트롤러의 {@code @SecurityRequirement(name = "bearerAuth")}가 가리키는 인증 방식을 여기서 한 번 정의합니다.
 * 운영 프로필에서는 {@code springdoc.api-docs.enabled=false}로 문서가 꺼지므로 이 설정도 외부에 노출되지 않습니다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    /** 컨트롤러가 인용하는 보안 스킴 이름. {@code Authorization: Bearer <accessToken>} */
    public static final String BEARER_AUTH = "bearerAuth";

    /**
     * 문서 제목과 Bearer 인증 스킴을 등록합니다.
     *
     * @return springdoc이 노출하는 OpenAPI 기본 문서
     */
    @Bean
    OpenAPI serviceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Komit Service API")
                        .version("0.1.0")
                        .description("실제 구현 문서. 계약의 기준은 목 서버 Swagger(/service/docs)이며 이 문서는 구현 검증용이다."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("로그인 응답의 accessToken. 만료 시간은 R-AUTH-01을 따른다.")));
    }
}
