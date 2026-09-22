package com.a501.service.domain.file.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.file.dto.response.UploadTargetResponse;
import com.a501.service.domain.file.dto.response.UploadedFileResponse;
import com.a501.service.domain.file.service.FileUploadService;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcSecurityTest(FileController.class)
class FileControllerTest {
    private static final String START_PATH = "/api/uploads";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private FileUploadService fileUploadService;

    @Test
    void startReturnsTheMockContract() throws Exception {
        when(fileUploadService.start(eq(12), any())).thenReturn(new UploadTargetResponse(
                512,
                "https://storage.komit.example.com/komit/evidence/test.pdf?signature=test",
                OffsetDateTime.parse("2026-08-14T18:17:00+09:00"),
                Map.of("Content-Type", "application/pdf")));

        mockMvc.perform(post(START_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalName":"재직증명서.pdf","contentType":"application/pdf",
                                 "sizeBytes":284193,"purpose":"CAREER_EVIDENCE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"uploadId":512,
                         "uploadUrl":"https://storage.komit.example.com/komit/evidence/test.pdf?signature=test",
                         "expiresAt":"2026-08-14T18:17:00+09:00",
                         "headers":{"Content-Type":"application/pdf"}}
                        """, true));
    }

    @Test
    void startRejectsMissingAndInvalidFieldsBeforeCallingTheService() throws Exception {
        mockMvc.perform(post(START_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalName":"","contentType":"application/pdf","sizeBytes":0}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"code":"INVALID_REQUEST","message":"요청 형식이 올바르지 않습니다.",
                         "errors":[{"field":"originalName","reason":"1자 이상이어야 합니다."},
                                   {"field":"purpose","reason":"필수 항목입니다."},
                                   {"field":"sizeBytes","reason":"1 이상이어야 합니다."}]}
                        """, true));

        verify(fileUploadService, never()).start(any(), any());
    }

    @Test
    void completeReturnsTheMockContract() throws Exception {
        when(fileUploadService.complete(12, 512)).thenReturn(new UploadedFileResponse(
                512,
                "재직증명서.pdf",
                "application/pdf",
                284193L,
                OffsetDateTime.parse("2026-08-14T18:12:00+09:00")));

        mockMvc.perform(post(START_PATH + "/512/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(12))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"fileId":512,"originalName":"재직증명서.pdf","contentType":"application/pdf",
                         "sizeBytes":284193,"verifiedAt":"2026-08-14T18:12:00+09:00"}
                        """, true));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post(START_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}
                        """, true));

        verify(fileUploadService, never()).start(any(), any());
    }
}
