package com.a501.service.domain.defaults.controller;

import static com.a501.service.common.security.SecuritySliceTestSupport.expiredToken;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.logging.TraceIdFilter;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.defaults.DefaultErrorCode;
import com.a501.service.domain.defaults.dto.request.DefaultSort;
import com.a501.service.domain.defaults.dto.response.DefaultListResponse;
import com.a501.service.domain.defaults.dto.response.DefaultResponse;
import com.a501.service.domain.defaults.service.DefaultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 기준 API의 계약 테스트. 기대 JSON은 목 서버({@code mock/app/routers/service.py})의 예시와 실제 응답을 글자 그대로 옮긴 것입니다.
 * 보안 체인까지 태워 401도 함께 검증합니다. (BACKEND_CONVENTIONS 19)
 */
@WebMvcSecurityTest(DefaultController.class)
class DefaultControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private DefaultService defaultService;

    @Test
    @DisplayName("단건 조회는 래퍼 없이 리소스를 그대로 돌려주고 traceId 헤더를 싣는다")
    void getDefaultReturnsResourceAsIs() throws Exception {
        when(defaultService.getDefault(1)).thenReturn(new DefaultResponse(1, "default"));

        mockMvc.perform(authenticated(get("/api/default/1")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, org.hamcrest.Matchers.matchesPattern("[0-9a-f]{32}")))
                .andExpect(content().json("{\"id\":1,\"message\":\"default\"}", true));
    }

    @Test
    @DisplayName("없거나 삭제된 레코드는 404 DEFAULT_NOT_FOUND다 (목의 id=2 사례)")
    void missingDefaultIsNotFound() throws Exception {
        when(defaultService.getDefault(2)).thenThrow(new BusinessException(DefaultErrorCode.DEFAULT_NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/default/2")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"code\":\"DEFAULT_NOT_FOUND\",\"message\":\"요청한 정보를 찾을 수 없습니다.\"}", true));
    }

    @Test
    @DisplayName("id가 정수가 아니면 422 INVALID_REQUEST에 필드 사유가 담긴다")
    void nonIntegerIdIsInvalidRequest() throws Exception {
        mockMvc.perform(authenticated(get("/api/default/abc")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json(
                        "{\"code\":\"INVALID_REQUEST\",\"message\":\"요청 형식이 올바르지 않습니다.\","
                                + "\"errors\":[{\"field\":\"id\",\"reason\":\"정수여야 합니다.\"}]}", true));

        verify(defaultService, never()).getDefault(any());
    }

    @Test
    @DisplayName("토큰이 없거나 만료되면 401 UNAUTHORIZED이고 서비스는 불리지 않는다")
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/default/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andExpect(content().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}", true));
        mockMvc.perform(get("/api/defaults").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken(12)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(defaultService, never()).getDefault(any());
        verify(defaultService, never()).getDefaults(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("목록 조회는 content와 페이지 정보로 감싼 목 예시와 같은 모양이다")
    void getDefaultsReturnsPagedContract() throws Exception {
        when(defaultService.getDefaults(1, 20, DefaultSort.ID_ASC)).thenReturn(new DefaultListResponse(
                List.of(new DefaultResponse(1, "default"), new DefaultResponse(2, "default")), 1, 20, 137, 7));

        mockMvc.perform(authenticated(get("/api/defaults")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"content":[{"id":1,"message":"default"},{"id":2,"message":"default"}],
                         "page":1,"size":20,"totalElements":137,"totalPages":7}
                        """, true));
    }

    @Test
    @DisplayName("파라미터를 생략하면 목과 같은 기본값(page=1, size=20, sort=id,asc)으로 조회한다")
    void getDefaultsUsesContractDefaults() throws Exception {
        when(defaultService.getDefaults(anyInt(), anyInt(), any())).thenReturn(emptyPage(1, 20));

        mockMvc.perform(authenticated(get("/api/defaults"))).andExpect(status().isOk());
        mockMvc.perform(authenticated(get("/api/defaults")).param("page", "3").param("size", "10").param("sort", "id,desc"))
                .andExpect(status().isOk());

        verify(defaultService).getDefaults(1, 20, DefaultSort.ID_ASC);
        verify(defaultService).getDefaults(3, 10, DefaultSort.ID_DESC);
    }

    @Test
    @DisplayName("page·size 범위 위반은 목과 같은 문구로 필드별로 나열한다")
    void pageAndSizeOutOfRangeAreListed() throws Exception {
        mockMvc.perform(authenticated(get("/api/defaults")).param("page", "0").param("size", "101"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"page","reason":"1 이상이어야 합니다."},
                                   {"field":"size","reason":"100 이하여야 합니다."}]}
                        """, true));
        mockMvc.perform(authenticated(get("/api/defaults")).param("size", "0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].reason").value("1 이상이어야 합니다."));
        mockMvc.perform(authenticated(get("/api/defaults")).param("page", "abc"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].reason").value("정수여야 합니다."));

        verify(defaultService, never()).getDefaults(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("sort가 허용 값이 아니면 목의 literal 문구 그대로 422다")
    void unsupportedSortIsInvalidRequest() throws Exception {
        mockMvc.perform(authenticated(get("/api/defaults")).param("sort", "message,asc"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"sort","reason":"'id,asc' or 'id,desc' 중 하나여야 합니다."}]}
                        """, true));

        verify(defaultService, never()).getDefaults(anyInt(), anyInt(), any());
    }

    private static DefaultListResponse emptyPage(int page, int size) {
        return new DefaultListResponse(List.of(), page, size, 0, 0);
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12));
    }
}
