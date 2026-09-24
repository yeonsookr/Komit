package com.a501.service.common.realtime.turn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 통화를 열 때 화면이 받아 가는 ICE 서버 목록.
 *
 * <p><b>자격은 짧게 삽니다</b> (`R-CON-19`). 통화가 그보다 길어지면 화면이 다시 받아 갈아
 * 끼웁니다. 그러라고 `expiresAt` 을 함께 줍니다 — 화면이 언제 다시 받아야 하는지 알 수 있는
 * 유일한 근거입니다.
 *
 * @param iceServers 브라우저에 그대로 넣는 목록
 * @param expiresAt 자격이 죽는 시각. 중계를 쓰지 않는 환경에서는 없습니다
 */
@Schema(description = "ICE 서버 목록")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IceServersResponse(List<IceServer> iceServers, Instant expiresAt) {
}
