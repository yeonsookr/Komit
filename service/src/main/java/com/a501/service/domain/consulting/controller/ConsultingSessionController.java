package com.a501.service.domain.consulting.controller;

import com.a501.service.common.realtime.turn.dto.IceServersResponse;
import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.consulting.dto.request.CreateSessionRecordingRequest;
import com.a501.service.domain.consulting.dto.response.ConsultingSessionResponse;
import com.a501.service.domain.consulting.dto.response.SessionParticipationResponse;
import com.a501.service.domain.consulting.dto.response.SessionRecordingResponse;
import com.a501.service.domain.consulting.dto.response.SessionSummaryResponse;
import com.a501.service.domain.consulting.service.ConsultingSessionService;
import com.a501.service.domain.consulting.service.SessionSummaryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "컨설팅 세션", description = "유료 상담 세션의 조회·입장·종료")
@RestController
@RequestMapping("/api/consulting-sessions")
@RequiredArgsConstructor
public class ConsultingSessionController {
    private final ConsultingSessionService sessionService;
    private final SessionSummaryQueryService summaryQueryService;

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
            summary = "세션 요약 조회",
            description =
                    """
                    세션이 끝난 뒤 만들어진 요약을 내려준다. 녹음을 글로 옮겨 정리한 결과다.

                    `changes`는 요청서에 반영할 수 있는 후보다. `discussed`는 반영 대상이 아니다.

                    - 200: 요약이 만들어졌을 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 SESSION_NOT_FOUND: 없는 세션이거나 요청자가 그 세션의 당사자가 아닐 때
                    - 409 SUMMARY_NOT_READY: 세션은 끝났지만 요약이 아직 준비되지 않았을 때
                    - 500 INTERNAL_ERROR: 요약을 읽지 못했을 때""")
    @GetMapping("/{sessionId}/summary")
    SessionSummaryResponse getSummary(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer sessionId) {
        return summaryQueryService.getSummary(user.userId(), sessionId);
    }

    @Operation(
            summary = "세션 참가자 기록",
            description =
                    """
                    이 세션에 동의한 사람들의 기록을 돌려준다.

                    **목록에 있다는 것이 곧 동의했다는 뜻이다 (R-CON-17).** 화면은 이것으로 \
                    자기가 이미 동의했는지 가린다 — 없으면 입장 안내를 보여 주고, 있으면 \
                    곧장 세션으로 보낸다. 통화 중에 새로고침해도 안내부터 다시 묻지 않는 \
                    근거가 이 목록이다.

                    상대의 기록도 함께 준다. 같은 세션의 두 사람이고, 「상대는 이미 동의했다」를 \
                    안내에 적으려면 필요하다 (R-CON-13).

                    - 200: 받았을 때. 아직 아무도 동의하지 않았으면 빈 목록이다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 SESSION_NOT_FOUND: 없는 세션이거나 요청자가 그 세션의 당사자가 아닐 때""")
    @GetMapping("/{sessionId}/participations")
    List<SessionParticipationResponse> participations(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer sessionId) {
        return sessionService.participations(user.userId(), sessionId);
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
                    - 409 SESSION_NOT_OPEN: 아직 이른 시각이거나, 이미 끝났거나, 계약이 성립하지 않은 세션일 때""")
    @PostMapping("/{sessionId}/participations")
    ResponseEntity<ConsultingSessionResponse> join(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer sessionId) {
        ConsultingSessionService.SessionEntry entry = sessionService.join(user.userId(), sessionId);
        return ResponseEntity.status(entry.entered() ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(entry.session());
    }

    @Operation(
            summary = "세션 녹음 올리기",
            description =
                    """
                    세션이 끝난 뒤 자기 마이크로 녹음한 파일을 올린다.

                    참가자마다 자기 트랙만 올린다. 두 사람 소리를 한 파일로 합치지 않는다 —                     파일이 곧 화자라서 나중에 누가 말했는지 가리는 데 따로 손이 들지 않는다.                     누가 올렸는지는 인증 정보로 정하므로 요청에 담지 않는다. (R-CON-14)

                    파일은 POST /api/uploads 로 올려 받은 fileId 로 넘긴다. 파일 자체는 이                     API 를 지나가지 않는다.

                    같은 파일을 다시 보내면 붙어 있는 것을 그대로 돌려준다. 올리고 나서 응답을                     받지 못해 다시 보내는 일이 있기 때문이다. 다른 파일로 바꾸려는 요청만                     거절한다.

                    - 201: 올렸을 때
                    - 400 SESSION_NOT_ENDED: 아직 끝나지 않은 세션일 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 SESSION_NOT_FOUND: 없는 세션이거나 요청자가 그 세션의 당사자가 아닐 때
                    - 409 RECORDING_ALREADY_UPLOADED: 이미 다른 녹음이 붙어 있을 때""")
    @PostMapping("/{sessionId}/recordings")
    @ResponseStatus(HttpStatus.CREATED)
    SessionRecordingResponse attachRecording(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer sessionId,
            @Valid @RequestBody CreateSessionRecordingRequest request) {
        return sessionService.attachRecording(user.userId(), sessionId, request.fileId());
    }

    @Operation(
            summary = "통화가 쓸 ICE 서버 받기",
            description =
                    """
                    브라우저가 상대와 연결할 때 쓰는 주소와 자격을 돌려준다. 그대로 \
                    RTCPeerConnection 에 넣는다.

                    **중계 자격은 한 통화보다 오래 산다 (R-CON-19).** 통화 도중에 죽으면 \
                    중계를 다시 얻을 길이 없어서다. 화면은 통화를 열 때 한 번 부르면 된다. \
                    expiresAt 은 그 전제가 깨졌는지 보라고 함께 준다.

                    자격은 발급한 뒤 취소할 수 없다. 그래서 세션 당사자에게만 준다 — 우리 \
                    서버로 남의 트래픽을 흘릴 수 있는 열쇠다.

                    중계 서버를 두지 않은 환경에서는 STUN 만 돌아오고 expiresAt 이 없다. \
                    통화는 직접 연결로 붙는다.

                    - 200: 받았을 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 SESSION_NOT_FOUND: 없는 세션이거나 요청자가 그 세션의 당사자가 아닐 때""")
    @GetMapping("/{sessionId}/ice-servers")
    IceServersResponse iceServers(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer sessionId) {
        return sessionService.iceServers(user.userId(), sessionId);
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
