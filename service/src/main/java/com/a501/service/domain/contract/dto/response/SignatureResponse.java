package com.a501.service.domain.contract.dto.response;

import com.a501.service.domain.contract.entity.ContractSignature;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 계약서 한쪽의 서명.
 *
 * @param userId 서명한 사람
 * @param isSigned 서명을 마쳤는지
 * @param signedAt 서명한 시각. 아직이면 {@code null}
 * @param signatureImageFileId 서명 이미지 파일. 아직이면 {@code null}
 */
@Schema(description = "계약서 한쪽의 서명.")
public record SignatureResponse(
        @Schema(description = "서명한 사용자 식별자.", example = "12") Integer userId,
        @Schema(description = "서명을 마쳤는지.", example = "true") boolean isSigned,
        @Schema(description = "서명한 시각. 아직이면 null 이다.") OffsetDateTime signedAt,
        @Schema(description = "서명 이미지 파일 식별자. 아직이면 null 이다.", example = "77")
                Integer signatureImageFileId) {

    /**
     * 엔티티를 응답으로 바꿉니다.
     *
     * @param entity 서명
     * @return 응답 DTO
     */
    public static SignatureResponse from(ContractSignature entity) {
        return new SignatureResponse(
                entity.getUserId(),
                entity.isSigned(),
                atUtc(entity.getSignedAt()),
                entity.getSignatureImageFileId());
    }

    /** 저장값에는 offset 이 없고 서버는 UTC 로 씁니다. 경계에서 붙입니다. (BACKEND_CONVENTIONS 11.1) */
    private static OffsetDateTime atUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
