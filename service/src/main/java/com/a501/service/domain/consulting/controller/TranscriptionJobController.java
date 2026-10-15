package com.a501.service.domain.consulting.controller;

import com.a501.service.config.OpenApiConfig;
import com.a501.service.domain.consulting.dto.request.TranscriptFailureRequest;
import com.a501.service.domain.consulting.dto.request.TranscriptResultRequest;
import com.a501.service.domain.consulting.dto.response.TranscriptionJobResponse;
import com.a501.service.domain.consulting.service.TranscriptionJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GPU 노트북만 부르는 경로. 목 서버에 두지 않는다 — 부르는 쪽이 화면이 아니다. 계약의 기준은
 * `ai/worker/docs/job-contract.md` 11절이다.
 */
@Tag(name = "전사 작업", description = "GPU 노트북이 전사할 세션을 받아 가고 결과를 올린다. 전사 토큰으로만 열린다")
@RestController
@RequestMapping("/api/transcription-jobs")
@RequiredArgsConstructor
public class TranscriptionJobController {
    private final TranscriptionJobService transcriptionJobService;

    @Operation(
            summary = "전사할 세션 받아 가기",
            description =
                    """
                    전사를 기다리는 세션 중 가장 먼저 끝난 것 하나를 녹음 읽기 주소와 함께 준다. \
                    끝났고, 두 사람의 녹음이 모두 붙었고, 전사도 전사 불가 판정도 없는 세션이다.

                    - 200: 할 일이 있을 때
                    - 204: 할 일이 없을 때
                    - 401 UNAUTHORIZED: 전사 토큰이 없거나 다를 때""",
            security = @SecurityRequirement(name = OpenApiConfig.TRANSCRIBER_AUTH))
    @PostMapping("/claim")
    ResponseEntity<TranscriptionJobResponse> claim() {
        return transcriptionJobService.claim()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(
            summary = "전사 결과 올리기",
            description =
                    """
                    두 녹음을 합친 발화 목록을 세션에 남긴다.

                    - 204: 남겼을 때. 이미 전사가 있거나 전사 불가로 판정된 세션이면 바꾸지 않고 204
                    - 401 UNAUTHORIZED: 전사 토큰이 없거나 다를 때
                    - 404 SESSION_NOT_FOUND: 없는 세션
                    - 422 INVALID_REQUEST: 본문이 형식과 다를 때""",
            security = @SecurityRequirement(name = OpenApiConfig.TRANSCRIBER_AUTH))
    @PostMapping("/{sessionId}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void result(@PathVariable Integer sessionId, @Valid @RequestBody TranscriptResultRequest request) {
        transcriptionJobService.recordResult(sessionId, request);
    }

    @Operation(
            summary = "전사 실패 알리기",
            description =
                    """
                    AUDIO_UNUSABLE 이면 전사 불가로 판정해 다시 내주지 않는다. \
                    TRANSCRIBER_UNAVAILABLE 이면 아무것도 바꾸지 않아 다음에 다시 나간다.

                    - 204: 받았을 때. 이미 전사가 있거나 전사 불가로 판정된 세션이면 바꾸지 않고 204
                    - 401 UNAUTHORIZED: 전사 토큰이 없거나 다를 때
                    - 404 SESSION_NOT_FOUND: 없는 세션
                    - 422 INVALID_REQUEST: 본문이 형식과 다를 때""",
            security = @SecurityRequirement(name = OpenApiConfig.TRANSCRIBER_AUTH))
    @PostMapping("/{sessionId}/failure")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void failure(@PathVariable Integer sessionId, @Valid @RequestBody TranscriptFailureRequest request) {
        transcriptionJobService.recordFailure(sessionId, request);
    }
}
