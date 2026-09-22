package com.a501.service.domain.contract.dto.response;

import com.a501.service.domain.contract.entity.Contract;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 계약서 한 건.
 *
 * <p>컨설팅과 개발이 계약 공통 부분(서명·전달 메시지·원문)을 함께 쓰고, 갈래별 내용만
 * {@code contractType} 에 따라 {@code consulting} 또는 {@code development} 한쪽에 담깁니다.
 * 나머지 한쪽은 항상 {@code null} 입니다.
 *
 * @param contractId 계약 id
 * @param roomId 이 계약이 달린 방
 * @param contractType 계약 종류 코드값
 * @param status 계약 진행 코드값
 * @param consulting 컨설팅 계약일 때의 내용
 * @param development 개발 계약일 때의 내용
 * @param clientSignature 의뢰인 서명
 * @param developerSignature 개발자 서명
 * @param messages 계약서에 딸려 오간 전달 메시지
 * @param contractText 체결된 계약서 원문. 체결 전이면 {@code null}
 * @param updatedAt 계약서를 마지막으로 고친 시각
 */
@Schema(description = "계약서 한 건. 갈래별 내용은 한쪽에만 담긴다.")
public record ContractResponse(
        @Schema(description = "계약 식별자.", example = "120") Integer contractId,
        @Schema(description = "이 계약이 달린 방 식별자.", example = "92") Integer roomId,
        @Schema(description = "계약 종류.", example = "CONSULTING") String contractType,
        @Schema(description = "계약 진행.", example = "NEGOTIATING") String status,
        @Schema(description = "컨설팅 계약일 때의 내용. 개발 계약이면 null 이다.")
                ConsultingAgreementResponse consulting,
        @Schema(description = "개발 계약일 때의 내용. 컨설팅 계약이면 null 이다.")
                DevelopmentAgreementResponse development,
        @Schema(description = "의뢰인 서명. 아직 없으면 null 이다.") SignatureResponse clientSignature,
        @Schema(description = "개발자 서명. 아직 없으면 null 이다.") SignatureResponse developerSignature,
        @Schema(description = "계약서에 딸려 오간 전달 메시지. 남긴 순서대로 내려간다.")
                List<TempMessageResponse> messages,
        @Schema(description = "체결된 계약서 원문. 체결 전이면 null 이다.") String contractText,
        @Schema(description = "계약서를 마지막으로 고친 시각.") OffsetDateTime updatedAt) {

    /**
     * 엔티티와 곁들인 값을 응답으로 바꿉니다.
     *
     * @param entity 계약
     * @param consulting 컨설팅 계약일 때의 내용
     * @param development 개발 계약일 때의 내용
     * @param clientSignature 의뢰인 서명
     * @param developerSignature 개발자 서명
     * @param messages 전달 메시지
     * @return 응답 DTO
     */
    public static ContractResponse of(
            Contract entity,
            ConsultingAgreementResponse consulting,
            DevelopmentAgreementResponse development,
            SignatureResponse clientSignature,
            SignatureResponse developerSignature,
            List<TempMessageResponse> messages) {
        return new ContractResponse(
                entity.getId(),
                entity.getRoomId(),
                entity.getContractType(),
                entity.getStatus(),
                consulting,
                development,
                clientSignature,
                developerSignature,
                messages,
                entity.getContractText(),
                atUtc(entity.getUpdatedAt()));
    }

    /** 저장값에는 offset 이 없고 서버는 UTC 로 씁니다. 경계에서 붙입니다. (BACKEND_CONVENTIONS 11.1) */
    private static OffsetDateTime atUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
