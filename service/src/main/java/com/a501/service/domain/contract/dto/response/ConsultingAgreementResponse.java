package com.a501.service.domain.contract.dto.response;

import com.a501.service.domain.consulting.dto.response.ConsultingSessionResponse;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 컨설팅 계약에만 있는 내용. 개발 계약이면 이 자리가 {@code null} 입니다.
 *
 * <p>요구사항 식별자를 담지 않습니다. 컨설팅은 요구사항 하나를 작업으로 만드는 것이 아니라 한 번
 * 만나 여러 갈래를 함께 보는 자리라, 요청서의 어느 줄에 매이지 않습니다.
 *
 * @param status 컨설팅 진행 코드값
 * @param totalAmount 이 계약으로 오가는 전체 금액
 * @param topic 이 세션에서 다룰 것
 * @param sessions 이 계약에 속한 세션
 */
@Schema(description = "컨설팅 계약에만 있는 내용. 개발 계약이면 이 자리가 null 이다.")
public record ConsultingAgreementResponse(
        @Schema(description = "컨설팅 진행.", example = "SIGNED") String status,
        @Schema(description = "이 계약으로 오가는 전체 금액. 속한 세션 금액의 합이다.", example = "50000")
                Long totalAmount,
        @Schema(description = "이 세션에서 다룰 것.", example = "회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다")
                String topic,
        @Schema(description = "이 계약에 속한 세션.") List<ConsultingSessionResponse> sessions) {

    /**
     * 엔티티와 곁들인 값을 응답으로 바꿉니다.
     *
     * @param entity 컨설팅 합의
     * @param sessions 컨설팅 도메인에서 받은 세션
     * @return 응답 DTO
     */
    public static ConsultingAgreementResponse of(
            ConsultingAgreement entity, List<ConsultingSessionResponse> sessions) {
        return new ConsultingAgreementResponse(
                entity.getStatus(),
                entity.getTotalAmount() == null ? null : entity.getTotalAmount().longValue(),
                entity.getTopic(),
                sessions);
    }
}
