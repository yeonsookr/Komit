package com.a501.service.domain.contract.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.contract.dto.request.AddDeliverableUrlRequest;
import com.a501.service.domain.contract.dto.request.AttachDeliverableFileRequest;
import com.a501.service.domain.contract.dto.response.DeliverableResponse;
import com.a501.service.domain.contract.service.DeliverableMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "계약", description = "개발 계약의 인계 자료")
@RestController
@RequestMapping("/api/deliverables")
@RequiredArgsConstructor
public class DeliverableController {
    private static final String OPEN_CONDITIONS =
            """
            - 401 UNAUTHORIZED: 로그인하지 않았을 때
            - 403 FORBIDDEN: 이 계약의 개발자가 아닐 때
            - 404 DELIVERABLE_NOT_FOUND: 없는 인계물이거나 이 계약의 당사자가 아닐 때
            - 409 CONTRACT_CANCELLED: 취소된 계약일 때
            - 409 HANDOVER_NOT_OPEN: 양쪽이 서명하기 전이거나 일반 작업을 모두 확정하기 전일 때
            - 409 FINAL_ACCEPTANCE_PASSED: 인계 작업을 확정해 최종 인수 시험을 통과한 뒤일 때""";

    private final DeliverableMaterialService deliverableMaterialService;

    @Operation(
            summary = "인계물 붙이기",
            description =
                    """
                    업로드 3콜을 마친 파일을 인계물에 붙인다. 열람 단계(TEST·PAID)는 올리는 개발자가 정한다. \
                    등록할 때마다 자료가 하나 늘고 같은 산출물의 URL과 다른 파일은 그대로 남는다. 같은 파일을 \
                    같은 단계로 다시 보내면 새로 만들지 않고 지금 상태를 돌려준다. (R-QA-06)

                    - 201: 붙였을 때
                    - 400 FILE_NOT_ATTACHABLE: 없는 파일이거나 업로드를 마치지 않았거나 남의 파일일 때
                    """
                            + OPEN_CONDITIONS
                            + """

                    - 409 MATERIAL_STAGE_CONFLICT: 같은 계약에 같은 파일이 다른 열람 단계로 있을 때
                    - 422 INVALID_REQUEST: fileId 또는 accessStage 가 없거나 허용 단계가 아닐 때""")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{deliverableId}/file")
    DeliverableResponse attachFile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer deliverableId,
            @Valid @RequestBody AttachDeliverableFileRequest request) {
        return deliverableMaterialService.attachFile(user.userId(), deliverableId, request);
    }

    @Operation(
            summary = "산출물 인수 시험 URL 추가",
            description =
                    """
                    인수 시험용 URL을 하나 더한다. URL 자료의 열람 단계는 TEST다. 같은 산출물의 다른 URL과 \
                    파일은 그대로 남는다. 같은 URL을 다시 보내면 새로 만들지 않고 지금 상태를 돌려준다. (R-QA-06)

                    - 201: 더했을 때
                    """
                            + OPEN_CONDITIONS
                            + """

                    - 422 INVALID_REQUEST: url 이 없거나 호스트가 있는 http·https 주소가 아닐 때""")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{deliverableId}/urls")
    DeliverableResponse addUrl(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer deliverableId,
            @Valid @RequestBody AddDeliverableUrlRequest request) {
        return deliverableMaterialService.addUrl(user.userId(), deliverableId, request);
    }
}
