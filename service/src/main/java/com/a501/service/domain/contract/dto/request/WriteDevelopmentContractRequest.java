package com.a501.service.domain.contract.dto.request;

import com.a501.service.domain.contract.dto.request.validation.DistinctMilestoneIssues;
import com.a501.service.domain.contract.dto.request.validation.SingleHandover;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 개발 계약서 작성 요청. 목 명세 {@code WriteDevelopmentContractRequest}와 같다.
 *
 * <p>계약에서 다룰 요구사항은 작업이 가리키는 것이다. 목록을 따로 받지 않는다. 따로 받으면 작업과 어긋난
 * 목록이 올 수 있다. 같은 요구사항을 두 작업에 담으면 {@link DistinctMilestoneIssues} 가 {@code milestones}
 * 필드 오류로 낸다.
 *
 * <p>납기일이 착수일보다 이른 것은 형식 오류가 아니라 400 {@code DUE_DATE_BEFORE_START} 다. 목이 그렇게
 * 정했다. 그래서 검증 애너테이션이 아니라 {@link #dueDateBeforeStart()} 로 두고 저장하는 쪽이 던진다.
 */
@Schema(description = "개발 계약서 작성 요청.")
@DistinctMilestoneIssues
@SingleHandover
public record WriteDevelopmentContractRequest(
        @Schema(description = "착수일.", example = "2026-08-26") @NotNull LocalDate startDate,
        @Schema(description = "최종 납기일.", example = "2026-09-16") @NotNull LocalDate dueDate,
        @Schema(description = "무상 하자보수 기간(일).", example = "14") @NotNull @PositiveOrZero
        Integer freeRepairDays,
        @Schema(description = "맡기로 한 작업. 가격의 합이 계약 금액이 된다. 하나 이상이어야 한다. "
                + "한 요구사항에는 작업이 하나라 같은 issueId 를 두 작업에 담을 수 없다.")
        @NotNull
        @Size(min = 1, message = "{komit.minItems}")
        List<@NotNull @Valid WriteMilestoneRequest> milestones,
        @Schema(description = "넘겨받을 인계물 이름. 이름마다 100자까지 받는다.", example = "[\"소스 파일 전체\", \"운영 설명서\"]")
        List<@NotNull @Size(min = 1, message = "{komit.minLength}") @Size(max = 100) String> deliverableTitles,
        @Schema(description = "계약서와 함께 남길 전달 메시지. 300자까지 받는다.") @Size(max = 300)
        String message) {

    public WriteDevelopmentContractRequest {
        milestones = withImpliedHandover(milestones);
    }

    /**
     * 한 릴리스 동안의 호환. 인계 작업 표시가 생기기 전 화면은 인계 작업을 표시 없이 0원 작업으로만 보낸다. 배포 중에 남은
     * 옛 화면이나 새로고침하지 않은 탭에서 계약서를 보내도 막히지 않도록, 표시가 하나도 없고 표시를 보내지 않은 0원 작업이
     * 정확히 하나면 그 작업을 인계 작업으로 본다. 모든 화면이 표시를 보내게 된 다음 릴리스에서 뺀다. (R-QA-06)
     */
    private static List<WriteMilestoneRequest> withImpliedHandover(List<WriteMilestoneRequest> milestones) {
        if (milestones == null || milestones.stream().anyMatch(m -> m != null && m.isHandover() != null)) {
            return milestones;
        }
        List<WriteMilestoneRequest> unpriced = milestones.stream()
                .filter(m -> m != null && m.price() != null && m.price() == 0)
                .toList();
        if (unpriced.size() != 1) {
            return milestones;
        }
        WriteMilestoneRequest handover = unpriced.getFirst();
        return milestones.stream().map(m -> m == handover ? m.asHandover() : m).toList();
    }

    /** 납기일이 착수일보다 이른지. 같은 날은 받는다. 목도 같은 날을 받는다. */
    public boolean dueDateBeforeStart() {
        return startDate != null && dueDate != null && dueDate.isBefore(startDate);
    }
}
