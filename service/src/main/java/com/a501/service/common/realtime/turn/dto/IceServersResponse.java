package com.a501.service.common.realtime.turn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 통화를 열 때 화면이 받아 가는 ICE 서버 목록.
 *
 * <p><b>자격은 한 통화보다 오래 삽니다</b> (`R-CON-19`). 통화 도중에 죽으면 중계를 다시 얻을
 * 길이 없어서입니다. 그래서 화면은 통화를 열 때 한 번 받고 그대로 씁니다.
 *
 * <p>`expiresAt` 은 그 전제가 깨졌는지 보라고 함께 줍니다. 통화가 이 시각을 넘기면 중계가
 * 끊길 수 있고, 그때는 수명을 늘리든 받아 오는 때를 늘리든 손을 대야 합니다.
 *
 * @param iceServers 브라우저에 그대로 넣는 목록
 * @param expiresAt 자격이 죽는 시각. 중계를 쓰지 않는 환경에서는 없습니다
 */
@Schema(description = "ICE 서버 목록")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IceServersResponse(List<IceServer> iceServers, Instant expiresAt) {
}
