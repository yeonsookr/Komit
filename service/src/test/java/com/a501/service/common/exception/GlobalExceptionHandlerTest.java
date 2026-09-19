package com.a501.service.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.config.JacksonConfig;
import com.a501.service.config.WebMvcConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실패 응답이 목 서버(mock/app/routers/service.py)와 같은 상태·코드·필드·문구인지 검증합니다. 기대값은 목 서버의 실제 응답입니다.
 * Boot는 테스트 클래스 안의 중첩 클래스를 스캔하지 않으므로 검증용 컨트롤러는 {@code @Import}로 올립니다.
 */
@WebMvcSecurityTest(GlobalExceptionHandlerTest.ProbeController.class)
@Import({JacksonConfig.class, WebMvcConfig.class, GlobalExceptionHandlerTest.ProbeController.class})
class GlobalExceptionHandlerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    @DisplayName("본문 검증 실패는 messages.properties 문구와 점 표기 경로로 나열한다")
    void bodyValidation() throws Exception {
        mockMvc.perform(json(post("/probe/body"))
                        .content("{\"nick\":\"aaaaaaaaaaaaaaaaaaaaaaaaa\",\"score\":9,\"items\":[{\"tags\":[]}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors.length()").value(5))
                .andExpect(jsonPath("$.errors[0].field").value("items.0.tags"))
                .andExpect(jsonPath("$.errors[0].reason").value("1개 이상이어야 합니다."))
                .andExpect(jsonPath("$.errors[1].field").value("items.0.title"))
                .andExpect(jsonPath("$.errors[1].reason").value("필수 항목입니다."))
                .andExpect(jsonPath("$.errors[2].field").value("name"))
                .andExpect(jsonPath("$.errors[2].reason").value("필수 항목입니다."))
                .andExpect(jsonPath("$.errors[3].field").value("nick"))
                .andExpect(jsonPath("$.errors[3].reason").value("20자 이하여야 합니다."))
                .andExpect(jsonPath("$.errors[4].field").value("score"))
                .andExpect(jsonPath("$.errors[4].reason").value("5 이하여야 합니다."));
        mockMvc.perform(json(post("/probe/body")).content("{\"name\":\"\",\"score\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].reason").value("1자 이상이어야 합니다."))
                .andExpect(jsonPath("$.errors[1].field").value("score"))
                .andExpect(jsonPath("$.errors[1].reason").value("1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("본문 필드의 타입 오류는 정수·문자열·불리언·허용 목록 문구다")
    void bodyTypeErrors() throws Exception {
        expectField(json(post("/probe/body")).content("{\"name\":123}"), "name", "문자열이어야 합니다.");
        expectField(json(post("/probe/body")).content("{\"name\":{\"a\":1}}"), "name", "문자열이어야 합니다.");
        expectField(json(post("/probe/body")).content("{\"name\":\"a\",\"count\":\"abc\"}"), "count", "정수여야 합니다.");
        expectField(json(post("/probe/body")).content("{\"name\":\"a\",\"flag\":\"maybe\"}"), "flag", "true 또는 false여야 합니다.");
        expectField(json(post("/probe/body")).content("{\"name\":\"a\",\"kind\":\"NOPE\"}"), "kind",
                "'CONSULT', 'DEVELOP' or 'REVIEW' 중 하나여야 합니다.");
        expectField(json(post("/probe/body")).content("{\"name\":\"a\",\"items\":[{\"title\":\"t\",\"tags\":[\"x\"]},{\"title\":7}]}"),
                "items.1.title", "문자열이어야 합니다.");
    }

    @Test
    @DisplayName("본문이 없거나 깨졌거나 객체가 아니면 body 필드로 알린다")
    void bodyItself() throws Exception {
        expectField(json(post("/probe/body")), "body", "필수 항목입니다.");
        expectField(json(post("/probe/body")).content("null"), "body", "필수 항목입니다.");
        expectField(json(post("/probe/body")).content("{\"name\": "), "body", "JSON 형식이 올바르지 않습니다.");
        expectField(json(post("/probe/body")).content("[1,2]"), "body", "값이 올바르지 않습니다.");
        expectField(post("/probe/body").header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.TEXT_PLAIN).content("{\"name\":\"a\"}"), "body", "값이 올바르지 않습니다.");
        expectField(json(post("/probe/body")).content("   "), "body", "JSON 형식이 올바르지 않습니다.");
        expectField(post("/probe/body").header(HttpHeaders.AUTHORIZATION, bearer())
                .header(HttpHeaders.CONTENT_TYPE, "application/*").content("{\"name\":\"a\"}"), "body", "값이 올바르지 않습니다.");
        expectField(post("/probe/body").header(HttpHeaders.AUTHORIZATION, bearer())
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data").content("x"), "body", "값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("쿼리·경로 값의 범위·타입·누락 오류")
    void queryAndPath() throws Exception {
        mockMvc.perform(get("/probe/query").header(HttpHeaders.AUTHORIZATION, bearer()).param("page", "0").param("size", "101"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("page"))
                .andExpect(jsonPath("$.errors[0].reason").value("1 이상이어야 합니다."))
                .andExpect(jsonPath("$.errors[1].field").value("size"))
                .andExpect(jsonPath("$.errors[1].reason").value("100 이하여야 합니다."));
        expectField(get("/probe/query").header(HttpHeaders.AUTHORIZATION, bearer()).param("page", "abc").param("size", "1"),
                "page", "정수여야 합니다.");
        expectField(get("/probe/query").header(HttpHeaders.AUTHORIZATION, bearer()).param("page", "1"), "size", "필수 항목입니다.");
        expectField(get("/probe/query").header(HttpHeaders.AUTHORIZATION, bearer()).param("page", "1").param("size", "1")
                .param("sort", "X"), "sort", "'CONSULT', 'DEVELOP' or 'REVIEW' 중 하나여야 합니다.");
        expectField(get("/probe/path/abc").header(HttpHeaders.AUTHORIZATION, bearer()), "id", "정수여야 합니다.");
    }

    @Test
    @DisplayName("없는 경로·메서드는 목과 같은 기본 본문이고, Accept가 JSON이 아니어도 JSON이다")
    void routing() throws Exception {
        mockMvc.perform(get("/probe/nope").header(HttpHeaders.AUTHORIZATION, bearer()).accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"detail\":\"Not Found\"}", true));
        mockMvc.perform(delete("/probe/query").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
                .andExpect(content().json("{\"detail\":\"Method Not Allowed\"}", true));
    }

    @Test
    @DisplayName("예상 밖 예외는 고정 문구의 500, 접근 거부는 403, 필터의 401은 같은 Content-Type이다")
    void otherFailures() throws Exception {
        mockMvc.perform(get("/probe/boom").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(
                        "{\"code\":\"INTERNAL_ERROR\",\"message\":\"요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.\"}", true));
        mockMvc.perform(get("/probe/denied").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}", true));
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}", true));
    }

    private void expectField(MockHttpServletRequestBuilder request, String field, String reason) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].reason").value(reason));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON);
    }

    private String bearer() {
        return "Bearer " + tokenProvider.issue(1);
    }

    enum ProbeKind { CONSULT, DEVELOP, REVIEW }

    record ProbeItem(@NotNull String title, @Size(min = 1, message = "{komit.minItems}") List<String> tags) {
    }

    record ProbeBody(
            @NotNull @Size(min = 1, message = "{komit.minLength}") String name,
            @Size(max = 20) String nick,
            @Min(1) @Max(5) Integer score,
            List<@Valid ProbeItem> items,
            ProbeKind kind,
            Boolean flag,
            Integer count) {
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {
        @PostMapping("/body")
        ProbeBody body(@Valid @RequestBody ProbeBody body) {
            return body;
        }

        @GetMapping("/query")
        String query(@RequestParam @Min(1) int page, @RequestParam @Min(1) @Max(100) int size,
                @RequestParam(required = false) ProbeKind sort) {
            return page + ":" + size + ":" + sort;
        }

        @GetMapping("/path/{id}")
        Long path(@PathVariable Long id) {
            return id;
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("denied");
        }
    }
}
