package com.a501.service.domain.consulting.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.consulting.service.TranscriptionJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 전사 토큰을 넣지 않은 환경. 노트북 없이 도는 로컬·CI 가 여기에 해당한다. */
@WebMvcSecurityTest(TranscriptionJobController.class)
@TestPropertySource(properties = "app.transcriber.token=")
class TranscriptionJobClosedTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TranscriptionJobService transcriptionJobService;

    @Test
    void 토큰을_두지_않으면_무엇을_보내도_닫혀_있다() throws Exception {
        mockMvc.perform(post("/api/transcription-jobs/claim").header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/transcription-jobs/claim").header(HttpHeaders.AUTHORIZATION, "Bearer x"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(transcriptionJobService);
    }
}
