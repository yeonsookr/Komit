package com.a501.service.common.security;

import java.io.IOException;

import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.exception.ErrorCode;
import com.a501.service.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 보안 필터 단계의 실패를 전역 예외 처리기와 같은 JSON으로 씁니다. 필터는 {@code @RestControllerAdvice}가 닿지 않습니다. */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {
    private final ObjectMapper objectMapper;

    public void unauthorized(HttpServletResponse response) throws IOException {
        write(response, CommonErrorCode.UNAUTHORIZED);
    }

    public void forbidden(HttpServletResponse response) throws IOException {
        write(response, CommonErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.status().value());
        // 컨트롤러 응답과 같은 Content-Type(application/json). JSON은 UTF-8이 규격이라 charset을 덧붙이지 않습니다.
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(objectMapper.writeValueAsBytes(ErrorResponse.of(errorCode)));
    }
}
