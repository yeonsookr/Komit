package com.a501.service.domain.contract.dto.response;

import com.a501.service.domain.contract.entity.DevelopmentContract;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 개발 계약에만 있는 내용. 컨설팅 계약이면 이 자리가 {@code null} 입니다.
 *
 * <p>{@code issueIds} 는 따로 저장하지 않습니다. 작업 하나가 요구사항 하나에 붙으므로 속한
 * 작업들이 가리키는 요구사항을 모은 것이 곧 이 계약에서 다루는 요구사항입니다.
 *
 * @param status 개발 진행 코드값
 * @param totalAmount 이 계약으로 오가는 전체 금액
 * @param issueIds 이 계약에서 다룰 요구사항
 * @param startDate 착수일. 정하기 전이면 {@code null}
 * @param dueDate 최종 납기일. 정하기 전이면 {@code null}
 * @param freeRepairDays 무상 하자보수 기간(일)
 * @param milestones 이 계약에 속한 작업
 * @param deliverables 넘겨받을 인계물
 */
@Schema(description = "개발 계약에만 있는 내용. 컨설팅 계약이면 이 자리가 null 이다.")
public record DevelopmentAgreementResponse(
        @Schema(description = "개발 진행.", example = "IN_PROGRESS") String status,
        @Schema(description = "이 계약으로 오가는 전체 금액.", example = "650000") Long totalAmount,
        @Schema(description = "이 계약에서 다룰 요구사항 식별자.", example = "[312, 313]")
                List<Integer> issueIds,
        @Schema(description = "착수일. 정하기 전이면 null 이다.", example = "2026-08-26") LocalDate startDate,
        @Schema(description = "최종 납기일. 정하기 전이면 null 이다.", example = "2026-09-16")
                LocalDate dueDate,
        @Schema(description = "무상 하자보수 기간(일).", example = "14") Integer freeRepairDays,
        @Schema(description = "이 계약에 속한 작업. 화면에 보여줄 순서대로 내려간다.")
                List<MilestoneResponse> milestones,
        @Schema(description = "넘겨받을 인계물.") List<DeliverableResponse> deliverables) {

    /**
     * 엔티티와 곁들인 값을 응답으로 바꿉니다.
     *
     * @param entity 개발 계약
     * @param issueIds 속한 작업들이 가리키는 요구사항
     * @param milestones 속한 작업
     * @param deliverables 인계물
     * @return 응답 DTO
     */
    public static DevelopmentAgreementResponse of(
            DevelopmentContract entity,
            List<Integer> issueIds,
            List<MilestoneResponse> milestones,
            List<DeliverableResponse> deliverables) {
        return new DevelopmentAgreementResponse(
                entity.getStatus(),
                entity.getTotalAmount() == null ? null : entity.getTotalAmount().longValue(),
                issueIds,
                entity.getStartDate(),
                entity.getDueDate(),
                entity.getFreeRepairDays(),
                milestones,
                deliverables);
    }
}
