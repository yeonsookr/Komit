package com.a501.service.domain.consulting.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.consulting.dto.response.ConsultingSessionResponse;
import com.a501.service.domain.consulting.service.ConsultingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "컨설팅 세션", description = "유료 상담 세션의 조회·입장·종료")
@RestController
@RequestMapping("/api/consulting-sessions")
@RequiredArgsConstructor
public class ConsultingSessionController {
    private final ConsultingSessionService sessionService;

    @Operation(
            summary = "세션 조회",
            description =
                    """
                    잡힌 일정과 시작·종료 시각을 돌려준다. 세션 화면은 방 밖에 있는 별도 화면이라 \
                    세션 식별자만으로 열린다.

                    - 200: 조회했을 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 SESSION_NOT_FOUND: 없는 세션이거나 요청자가 그 세션의 당사자가 아닐 때""")
    @GetMapping("/{sessionId}")
    ConsultingSessionResponse getSession(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer sessionId) {
        return sessionService.getSession(user.userId(), sessionId);
    }

    @Operation(
            summary = "세션 입장",
            description =
                    """
                    이 요청 자체가 녹음·전사·요약 동의와 입장 요청을 겸한다. 의뢰인과 개발자 양쪽의 \
                    요청이 모두 모이기 전에는 실제로 입장시키지 않는다. 같은 참가자가 기다리는 동안 \
                    다시 요청해도 동의는 한 번만 기록한다.

                    화상 연결 자체는 이 API 가 하지 않는다. 들어갈 수 있는지 판정하고 기록만 남긴다.

                    - 201: 양쪽이 동의했고 실제로 들어갔을 때. 최초 입장이면 startedAt 이 채워진다
                    - 202: 내 동의는 기록됐지만 상대방 동의를 기다릴 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 SESSION_NOT_FOUND: 없는 세션이거나 요청자가 그 세션의 당사자가 아닐 때
                    - 409 SESSION_NOT_OPEN: 아직 이른 시각이거나 이미 끝난 세션일 때""")
    @PostMapping("/{sessionId}/participations")
    ResponseEntity<ConsultingSessionResponse> join(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer sessionId) {
        ConsultingSessionService.SessionEntry entry = sessionService.join(user.userId(), sessionId);
        return ResponseEntity.status(entry.entered() ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(entry.session());
    }

    @Operation(
            summary = "세션 종료",
            description =
                    """
                    세션을 마치고 끝난 시각을 기록한다. 같은 요청을 다시 보내도 최초 종료 시각과 \
                    같은 결과가 돌아온다.

                    종료 뒤 참가자들이 각자 녹음을 올린다. 전사와 요약은 양쪽 녹음이 준비되거나 누락 \
                    대기 조건을 충족한 뒤 시작하므로 종료 응답에 녹음 파일을 싣지 않는다.

                    - 200: 세션 종료가 확정됐을 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 SESSION_NOT_FOUND: 없는 세션이거나 요청자가 그 세션의 당사자가 아닐 때""")
    @PostMapping("/{sessionId}/end")
    ConsultingSessionResponse end(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer sessionId) {
        return sessionService.end(user.userId(), sessionId);
    }
}
