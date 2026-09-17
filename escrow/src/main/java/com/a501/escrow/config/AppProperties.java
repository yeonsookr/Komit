package com.a501.escrow.config;

import java.net.URI;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 배포 환경이 주입하는 앱 공통 값입니다.
 *
 * @param publicBaseUrl 이 서버가 밖에서 보이는 주소. nginx 뒤에서는 경로 접두사까지 포함합니다. (예: https://komit.site/escrow)
 *                      절대 URL을 요청 문맥 없이 만들어야 할 때 이 값을 씁니다.
 *                      환경변수 APP_PUBLIC_BASE_URL 이 없으면 기동에 실패합니다.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull URI publicBaseUrl) {
}
