package com.a501.service.domain.contract.dto.response;

import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.diagnosis.dto.IssueBrief;
import com.a501.service.domain.diagnosis.dto.response.QaCriterionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 계약에 속한 작업 하나.
 *
 * <p>작업은 요구사항 하나에 붙습니다. 이름과 설명은 계약서에서 바꿨으면 작업에 적힌 값을, 아니면
 * 요구사항의 값을 씁니다. 요청서는 계약서가 건드리지 않아 바꾼 값은 작업에만 있습니다. 위치는
 * 요구사항의 값입니다.
 *
 * @param milestoneId 작업 id
 * @param issueId 붙은 요구사항 id. 계약서를 다시 쓸 때 이 값을 그대로 보낸다. 없으면 {@code null}
 * @param title 작업 이름. 계약서에서 바꾼 이름, 없으면 붙은 요구사항의 제목
 * @param detail 작업 설명. 계약서에서 바꾼 설명, 없으면 붙은 요구사항의 설명. 둘 다 없으면 {@code null}
 * @param price 작업 가격
 * @param location 문제가 있는 위치. 없으면 {@code null}
 * @param workDate 이 작업에 잡은 기간(일). 정하지 않았으면 {@code null}
 * @param status 작업 진행 코드값
 * @param rejectMessage 거부 사유. 거부되지 않았으면 {@code null}
 * @param submittedAt 확인을 요청한 시각. 아직이면 {@code null}
 * @param confirmedAt 의뢰인이 통과시킨 시각. 아직이면 {@code null}
 * @param autoPassAt 무응답 시 자동 통과되는 시각. 확인 대기 중일 때만 채워집니다
 * @param qaCriteria 이 작업의 확인 문장
 */
@Schema(description = "계약에 속한 작업 하나.")
public record MilestoneResponse(
        @Schema(description = "작업 식별자.", example = "301") Integer milestoneId,
        @Schema(
                        description = "붙은 요구사항 식별자. 계약서를 다시 쓸 때 이 값을 그대로 보낸다."
                                + " 요구사항에 붙지 않은 작업이면 null 이다.",
                        example = "312")
                Integer issueId,
        @Schema(description = "작업 이름.", example = "1차 · 관리자 API에 인증 추가") String title,
        @Schema(description = "작업 설명. 없으면 null 이다.") String detail,
        @Schema(description = "작업 가격. 인계 작업은 0 이다.", example = "400000") Long price,
        @Schema(description = "인계 작업이면 true 다. 인계 작업은 계약서마다 하나이고 0원이다. 금액이 아니라 이 값으로 인계 작업을 가린다. (R-QA-06)",
                        example = "false")
                boolean isHandover,
        @Schema(description = "문제가 있는 위치. 없으면 null 이다.", example = "/admin") String location,
        @Schema(description = "이 작업에 잡은 기간(일). 정하지 않았으면 null 이다.", example = "10")
                Integer workDate,
        @Schema(description = "작업 진행.", example = "IN_PROGRESS") String status,
        @Schema(description = "거부 사유. 거부되지 않았으면 null 이다.") String rejectMessage,
        @Schema(description = "확인을 요청한 시각. 아직이면 null 이다.") OffsetDateTime submittedAt,
        @Schema(description = "의뢰인이 통과시킨 시각. 아직이면 null 이다.") OffsetDateTime confirmedAt,
        @Schema(description = "무응답 시 자동 통과되는 시각. 확인 대기 중일 때만 채워진다. (R-QA-02)")
                OffsetDateTime autoPassAt,
        @Schema(description = "이 작업의 확인 문장.") List<QaCriterionResponse> qaCriteria) {

    /** R-QA-02: 확인 요청 후 무응답이면 3일 뒤 자동 통과된다. */
    private static final int AUTO_PASS_DAYS = 3;

    /**
     * 엔티티와 곁들인 값을 응답으로 바꿉니다.
     *
     * @param entity 작업
     * @param issue 붙은 요구사항. 없으면 {@code null}
     * @param qaCriteria 이 작업의 확인 문장
     * @return 응답 DTO
     */
    public static MilestoneResponse of(
            Milestone entity, IssueBrief issue, List<QaCriterionResponse> qaCriteria) {
        return new MilestoneResponse(
                entity.getId(),
                entity.getIssueId(),
                entity.getTitle() != null ? entity.getTitle() : issue == null ? null : issue.title(),
                entity.getDetail() != null ? entity.getDetail() : issue == null ? null : issue.detail(),
                entity.getPrice() == null ? null : entity.getPrice().longValue(),
                entity.isHandover(),
                issue == null ? null : issue.location(),
                entity.getWorkDate(),
                entity.getStatus(),
                entity.getRejectMessage(),
                atUtc(entity.getSubmittedAt()),
                atUtc(entity.getConfirmedAt()),
                autoPassAt(entity),
                qaCriteria);
    }

    /**
     * 확인을 요청했고 아직 통과되지 않았을 때만 자동 통과 시각이 있습니다. 통과된 뒤에는 기다릴
     * 것이 없어 비웁니다.
     */
    private static OffsetDateTime autoPassAt(Milestone entity) {
        if (entity.getSubmittedAt() == null || entity.getConfirmedAt() != null) {
            return null;
        }
        return atUtc(entity.getSubmittedAt().plusDays(AUTO_PASS_DAYS));
    }

    /** 저장값에는 offset 이 없고 서버는 UTC 로 씁니다. 경계에서 붙입니다. (BACKEND_CONVENTIONS 11.1) */
    private static OffsetDateTime atUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
