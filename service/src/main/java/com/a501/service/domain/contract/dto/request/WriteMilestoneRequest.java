package com.a501.service.domain.contract.dto.request;

import com.a501.service.domain.contract.dto.request.validation.MilestonePrice;
import com.a501.service.domain.contract.dto.request.validation.NewWorkTitle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 계약서에 담을 작업 하나. 목 명세 {@code WriteMilestoneRequest}와 같다.
 *
 * <p><b>작업은 언제나 요구사항 하나에 붙는다.</b> 요청서에 있던 요구사항이면 {@code issueId} 를 보내고,
 * 개발방에서 새로 합의한 작업이면 {@code issueId} 없이 {@code title} 을 보낸다. 새 작업은 서버가 계약용
 * 요구사항을 만들어 붙인다. 새 작업에 이름이 없으면 {@link NewWorkTitle} 이 {@code title} 필드 오류로 낸다.
 *
 * <p>{@code title}·{@code detail} 은 계약서에서 쓰는 이름·설명이다. 기존 요구사항에서 보내지 않으면 그
 * 요구사항의 이름·설명을 쓴다. 계약서에서 바꿔도 요청서는 바뀌지 않는다.
 *
 * <p>인계 작업은 {@code isHandover} 로 표시하고 0원이어야 한다. 일반 작업은 1원 이상이다. 어기면
 * {@link MilestonePrice} 가 {@code price} 필드 오류로 낸다. (R-QA-06)
 */
@Schema(description = "계약서에 담을 작업 하나.")
@NewWorkTitle
@MilestonePrice
public record WriteMilestoneRequest(
        @Schema(description = "붙일 요구사항 식별자. 개발방에서 새로 합의한 작업이면 보내지 않는다.", example = "312")
        Integer issueId,
        @Schema(
                description = "작업 이름. 새 작업이면 반드시 보낸다. 기존 요구사항이면 계약서에서 바꾼 이름만 보내고, "
                        + "보내지 않으면 요구사항 이름을 쓴다.",
                example = "관리자 API에 인증 추가")
        @Size(min = 1, message = "{komit.minLength}") @Size(max = 200) String title,
        @Schema(description = "작업 설명. 적지 않아도 된다. 기존 요구사항이면 보내지 않았을 때 요구사항 설명을 쓴다.")
        @Size(min = 1, message = "{komit.minLength}") @Size(max = 1000) String detail,
        @Schema(
                description = "인계 작업이면 true로 보낸다. 인계 작업은 0원이어야 하고 계약서마다 하나 있어야 한다. "
                        + "보내지 않으면 일반 작업이다. (R-QA-06)",
                example = "false")
        Boolean isHandover,
        @Schema(description = "작업 가격. 인계 작업은 0으로 보내고 일반 작업은 1원 이상이다.", example = "400000")
        @NotNull @PositiveOrZero Integer price,
        @Schema(description = "이 작업에 잡은 기간(일). 최종 납기일이 어떻게 나왔는지 보여주는 참고값이라 정하지 않아도 된다.", example = "10")
        @PositiveOrZero Integer workDate,
        @Schema(description = "이 작업의 확인 문장. 하나 이상이어야 하고 문장마다 500자까지 받는다.")
        @NotNull
        @Size(min = 1, message = "{komit.minItems}")
        List<@NotNull @Size(min = 1, message = "{komit.minLength}") @Size(max = 500) String> qaCriteria) {}
