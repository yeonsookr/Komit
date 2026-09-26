package com.a501.service.domain.contract.dto.request;

import com.a501.service.domain.contract.dto.request.validation.DistinctMilestoneIssues;
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

    /** 납기일이 착수일보다 이른지. 같은 날은 받는다. 목도 같은 날을 받는다. */
    public boolean dueDateBeforeStart() {
        return startDate != null && dueDate != null && dueDate.isBefore(startDate);
    }
}
