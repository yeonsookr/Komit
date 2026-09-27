package com.a501.service.domain.contract.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.contract.dto.request.ConfirmMilestoneRequest;
import com.a501.service.domain.contract.dto.request.SaveQaResultsRequest;
import com.a501.service.domain.contract.dto.response.MilestoneResponse;
import com.a501.service.domain.contract.service.MilestoneProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "계약", description = "개발 작업의 확인")
@RestController
@RequestMapping("/api/milestones")
@RequiredArgsConstructor
public class MilestoneController {
    private final MilestoneProgressService milestoneProgressService;

    @Operation(
            summary = "작업 확인 요청",
            description =
                    """
                    개발자가 착수한 작업을 마치고 의뢰인에게 확인을 요청한다. 통과하지 않은 작업은 순서와 관계없이 \
                    요청할 수 있다. 이때부터 기한을 세고 의뢰인이 답하지 않으면 자동으로 통과된다. 다시 요청하면 \
                    기한도 다시 센다. 바디는 받지 않는다. (R-QA-02)

                    인계 작업은 일반 작업을 모두 통과한 뒤, 계약 전체에 인수 시험 자료(URL 또는 TEST 파일)와 \
                    PAID 파일이 하나 이상씩 있을 때 요청한다. 요청한 뒤로는 인계 자료를 바꿀 수 없다. (R-QA-06)

                    - 201: 요청했을 때. 상태가 SUBMITTED 가 되고 submittedAt·autoPassAt 이 채워진다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 403 FORBIDDEN: 이 계약의 개발자가 아닐 때
                    - 404 MILESTONE_NOT_FOUND: 없는 작업이거나 이 계약의 당사자가 아닐 때
                    - 409 MILESTONE_STATE_CONFLICT: 착수 전이거나 이미 통과한 작업일 때
                    - 409 HANDOVER_NOT_OPEN: 인계 작업인데 일반 작업을 모두 통과하기 전일 때
                    - 409 HANDOVER_MATERIALS_MISSING: 인계 작업인데 인수 시험 자료나 PAID 파일이 없을 때
                    - 422 INVALID_REQUEST: milestoneId 가 정수가 아닐 때""")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{milestoneId}/submission")
    MilestoneResponse submit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer milestoneId) {
        return milestoneProgressService.submit(milestoneId, user.userId());
    }

    @Operation(
            summary = "확인 결과 저장",
            description =
                    """
                    의뢰인이 확인 문장을 직접 해보고 체크한 것을 저장한다. 다 체크하지 않아도 보낼 수 있고, 이미 \
                    통과한 것에 더하기만 한다. 체크는 되돌리지 않는다.

                    **확인 요청 중에 체크하지 않은 문장이 남으면 작업이 개발자에게 돌아간다.** 상태가 \
                    IN_PROGRESS 가 되고 submittedAt·autoPassAt 이 비어 자동 통과를 멈춘다. 개발자가 고쳐 다시 \
                    요청한다. 확인 요청 전 작업에 남긴 결과는 상태를 바꾸지 않는다. 전부 통과한 작업은 이 \
                    API 가 아니라 작업 통과시키기로 완료한다. (R-QA-01)

                    - 200: 저장했을 때. 저장한 뒤의 작업을 준다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 403 FORBIDDEN: 이 계약의 의뢰인이 아닐 때
                    - 404 MILESTONE_NOT_FOUND: 없는 작업이거나 이 계약의 당사자가 아닐 때
                    - 409 MILESTONE_STATE_CONFLICT: 착수 전이거나 이미 통과한 작업일 때 (R-QA-03)
                    - 422 INVALID_REQUEST: qaCriterionIds 가 없거나 비었거나, 이 작업의 확인 문장이 아닌 값이 \
                    섞였을 때""")
    @PutMapping("/{milestoneId}/qa-results")
    MilestoneResponse saveQaResults(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer milestoneId,
            @Valid @RequestBody SaveQaResultsRequest request) {
        return milestoneProgressService.saveQaResults(milestoneId, user.userId(), request);
    }

    @Operation(
            summary = "작업 통과시키기",
            description =
                    """
                    의뢰인이 확인 문장을 모두 체크하고 작업을 통과시킨다. **통과시키면 되돌릴 수 없다.** 인계 작업을 \
                    통과시키면 최종 인수 시험 통과다. 대금은 여기서 나가지 않는다. (R-QA-01, R-QA-03, R-QA-06)

                    - 201: 통과시켰을 때. 상태가 CONFIRMED 가 되고 confirmedAt 이 채워진다
                    - 400 QA_NOT_ALL_CHECKED: 이 작업의 확인 문장이 모두 들어 있지 않을 때. 빈 목록도 여기다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 403 FORBIDDEN: 이 계약의 의뢰인이 아닐 때
                    - 404 MILESTONE_NOT_FOUND: 없는 작업이거나 이 계약의 당사자가 아닐 때
                    - 409 MILESTONE_STATE_CONFLICT: 확인 요청 중인 작업이 아닐 때
                    - 422 INVALID_REQUEST: qaCriterionIds 가 없거나, 이 작업의 확인 문장이 아닌 값이 섞였을 때""")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{milestoneId}/confirmation")
    MilestoneResponse confirm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer milestoneId,
            @Valid @RequestBody ConfirmMilestoneRequest request) {
        return milestoneProgressService.confirm(milestoneId, user.userId(), request);
    }
}
