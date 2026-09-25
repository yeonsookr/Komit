package com.a501.service.domain.consulting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.SecuritySliceTestSupport;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.dto.Speaker;
import com.a501.service.domain.consulting.dto.request.TranscriptFailureRequest;
import com.a501.service.domain.consulting.dto.response.TranscriptionJobResponse;
import com.a501.service.domain.consulting.service.TranscriptionJobService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcSecurityTest(TranscriptionJobController.class)
class TranscriptionJobControllerTest {
    private static final String TRANSCRIBER = "Bearer " + SecuritySliceTestSupport.TRANSCRIBER_TOKEN;
    private static final String UTTERANCE =
            "{\"utteranceIndex\": 0, \"speaker\": \"CLIENT\", \"startMs\": 5680, \"endMs\": 6540, "
                    + "\"rawText\": \"안녕하세요.\"}";
    private static final String RESULT = "{\"utterances\": [" + UTTERANCE + "]}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private TranscriptionJobService transcriptionJobService;

    @Test
    void 할_일을_녹음_주소와_함께_준다() throws Exception {
        when(transcriptionJobService.claim()).thenReturn(Optional.of(new TranscriptionJobResponse(55, List.of(
                new TranscriptionJobResponse.Track(Speaker.CLIENT, "https://file.example/a"),
                new TranscriptionJobResponse.Track(Speaker.DEVELOPER, "https://file.example/b")))));

        mockMvc.perform(post("/api/transcription-jobs/claim").header(HttpHeaders.AUTHORIZATION, TRANSCRIBER))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        """
                        {"sessionId": 55, "tracks": [
                          {"speaker": "CLIENT", "audioUrl": "https://file.example/a"},
                          {"speaker": "DEVELOPER", "audioUrl": "https://file.example/b"}]}""",
                        true));
    }

    @Test
    void 할_일이_없으면_204() throws Exception {
        when(transcriptionJobService.claim()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/transcription-jobs/claim").header(HttpHeaders.AUTHORIZATION, TRANSCRIBER))
                .andExpect(status().isNoContent());
    }

    @Test
    void 전사_토큰이_없거나_다르면_401() throws Exception {
        mockMvc.perform(post("/api/transcription-jobs/claim"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/transcription-jobs/claim").header(HttpHeaders.AUTHORIZATION, "Bearer wrong"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(transcriptionJobService);
    }

    @Test
    void 사용자_토큰으로는_전사_경로가_열리지_않는다() throws Exception {
        mockMvc.perform(post("/api/transcription-jobs/claim")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(7)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(transcriptionJobService);
    }

    @Test
    void 전사_토큰으로는_다른_경로가_열리지_않는다() throws Exception {
        mockMvc.perform(post("/api/realtime/tickets").header(HttpHeaders.AUTHORIZATION, TRANSCRIBER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 결과를_남기면_204() throws Exception {
        mockMvc.perform(json(post("/api/transcription-jobs/55/result"), RESULT))
                .andExpect(status().isNoContent());

        verify(transcriptionJobService).recordResult(eq(55), any());
    }

    @Test
    void 발화가_하나도_없어도_받는다() throws Exception {
        mockMvc.perform(json(post("/api/transcription-jobs/55/result"), "{\"utterances\": []}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 모르는_화자면_422() throws Exception {
        mockMvc.perform(json(post("/api/transcription-jobs/55/result"), RESULT.replace("CLIENT", "OPERATOR")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(transcriptionJobService, never()).recordResult(any(), any());
    }

    @Test
    void 발화_필드가_빠지면_422() throws Exception {
        String noText = RESULT.replace(", \"rawText\": \"안녕하세요.\"", "");

        mockMvc.perform(json(post("/api/transcription-jobs/55/result"), noText))
                .andExpect(status().isUnprocessableContent());

        verify(transcriptionJobService, never()).recordResult(any(), any());
    }

    @Test
    void 없는_세션이면_404() throws Exception {
        doThrow(new BusinessException(ConsultingErrorCode.SESSION_NOT_FOUND))
                .when(transcriptionJobService).recordResult(eq(404), any());

        mockMvc.perform(json(post("/api/transcription-jobs/404/result"), RESULT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void 실패를_받으면_204() throws Exception {
        mockMvc.perform(json(post("/api/transcription-jobs/55/failure"),
                        "{\"code\": \"AUDIO_UNUSABLE\", \"message\": \"CLIENT.webm 를 읽지 못했습니다\"}"))
                .andExpect(status().isNoContent());

        verify(transcriptionJobService).recordFailure(55, new TranscriptFailureRequest(
                TranscriptFailureRequest.Code.AUDIO_UNUSABLE, "CLIENT.webm 를 읽지 못했습니다"));
    }

    @Test
    void 모르는_실패_코드면_422() throws Exception {
        mockMvc.perform(json(post("/api/transcription-jobs/55/failure"), "{\"code\": \"OOPS\", \"message\": \"x\"}"))
                .andExpect(status().isUnprocessableContent());

        verify(transcriptionJobService, never()).recordFailure(any(), any());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.header(HttpHeaders.AUTHORIZATION, TRANSCRIBER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
