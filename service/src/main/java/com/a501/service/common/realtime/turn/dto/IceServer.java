package com.a501.service.common.realtime.turn.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 브라우저가 그대로 `RTCPeerConnection` 에 넣는 ICE 서버 한 줄.
 *
 * <p>필드 이름은 브라우저 규격을 따릅니다. 우리 마음대로 바꾸면 화면이 다시 옮겨 담아야 합니다.
 *
 * @param urls 같은 자격으로 쓰는 주소들
 * @param username 시한부 자격의 아이디. STUN 에는 없습니다
 * @param credential 시한부 자격의 비밀번호. STUN 에는 없습니다
 */
@Schema(description = "ICE 서버 한 줄")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IceServer(
        @Schema(example = "[\"turn:turn.komit.site:3478?transport=udp\"]") List<String> urls,
        @Schema(example = "1790238175:12") String username,
        @Schema(example = "8mCPiF2R1qkHrDbY0dzXqA1sZ0w=") String credential) {

    /** STUN 은 자격을 받지 않습니다. 두 필드를 비워 두면 응답에서 빠집니다. */
    public static IceServer withoutCredential(List<String> urls) {
        return new IceServer(List.copyOf(urls), null, null);
    }

    public static IceServer withCredential(List<String> urls, String username, String credential) {
        return new IceServer(List.copyOf(urls), username, credential);
    }
}
