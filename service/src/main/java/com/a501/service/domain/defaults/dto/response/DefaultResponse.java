package com.a501.service.domain.defaults.dto.response;

import com.a501.service.domain.defaults.entity.Default;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 단건 조회 성공 응답. 목 명세 {@code DefaultResponse}와 같은 두 필드만 가지며 공통 래퍼로 감싸지 않습니다.
 *
 * <p>QueryDSL 목록 조회가 {@code Projections.constructor}로 이 생성자를 직접 호출하므로 필드 순서(id, message)를 바꾸면
 * {@link com.a501.service.domain.defaults.repository.DefaultRepositoryImpl}도 함께 고쳐야 합니다.
 *
 * @param id      예시 레코드 id. 어떤 도메인의 식별자도 아닙니다.
 * @param message 저장된 문구. 목은 고정값 {@code "default"}를 돌려줍니다.
 */
@Schema(description = "단건 조회 성공 응답. 공통 래퍼 없이 리소스를 그대로 반환한다.")
public record DefaultResponse(
        @Schema(description = "요청한 default 예시 id. 어떤 도메인의 식별자도 아니다.", example = "1") Integer id,
        @Schema(description = "저장된 문구.", example = "default") String message) {

    /**
     * 엔티티를 응답으로 바꿉니다. 엔티티는 밖으로 나가지 않습니다.
     *
     * @param entity 조회한 엔티티
     * @return 응답 DTO
     */
    public static DefaultResponse from(Default entity) {
        return new DefaultResponse(entity.getId(), entity.getMessage());
    }
}
