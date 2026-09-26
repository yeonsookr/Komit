package com.a501.service.domain.contract.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.contract.dto.request.WriteConsultingContractRequest;
import com.a501.service.domain.contract.dto.request.WriteDevelopmentContractRequest;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.service.ConsultingContractWriteService;
import com.a501.service.domain.contract.service.ContractDraftService;
import com.a501.service.domain.contract.service.DevelopmentContractWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "계약", description = "계약서 작성")
@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class RoomContractController {
    private final ConsultingContractWriteService consultingContractWriteService;
    private final ContractDraftService contractDraftService;
    private final DevelopmentContractWriteService developmentContractWriteService;

    @Operation(
            summary = "컨설팅 계약서 초안 생성",
            description = "전환 전 무료 상담 전체 대화로 계약서 version 1을 만든다. 같은 방에서 다시 호출하면 저장된 계약서를 반환한다.")
    @PostMapping("/contract-draft")
    ContractResponse draftContract(
            @Parameter(description = "컨설팅 방 식별자.", example = "92") @PathVariable Integer roomId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return contractDraftService.generate(roomId, user.userId());
    }

    @Operation(
            summary = "컨설팅 계약서 작성",
            description =
                    """
                    개발자가 컨설팅 계약서를 써서 의뢰인에게 보낸다. 다룰 항목, 세션 일시, 세션 \
                    시간, 금액을 담는다. 보내면 의뢰인 화면에 협의중으로 뜬다.

                    **계약서는 개발자만 쓴다.** 의뢰인은 받아서 서명하거나 수정을 제안한다. 방의 \
                    두 사람 모두 방이 있다는 것을 알고 있으므로, 개발자가 아닌 사람에게는 감추지 \
                    않고 403 으로 답한다.

                    - 201: 계약서를 보냈을 때. 아직 아무도 서명하지 않아 서명 두 개가 모두 null 이다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 403 FORBIDDEN: 이 방의 개발자가 아닐 때
                    - 404 ROOM_NOT_FOUND: 없는 방일 때
                    - 422 INVALID_REQUEST: scheduledAt 이 지난 시각이거나 price 가 음수일 때""")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/consulting-contracts")
    ContractResponse writeConsultingContract(
            @Parameter(description = "방 식별자.", example = "92") @PathVariable Integer roomId,
            @Valid @RequestBody WriteConsultingContractRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return consultingContractWriteService.write(roomId, user.userId(), request);
    }

    @Operation(
            summary = "개발 계약서 작성",
            description =
                    """
                    개발자가 개발 계약서를 써서 의뢰인에게 보낸다. 작업마다 가격과 확인 문장을 붙이고, \
                    작업 가격의 합이 계약 금액이 된다. 협의 중에 다시 보내면 그 계약서를 이 요청으로 바꾼다.

                    **작업은 언제나 요구사항 하나에 붙는다.** 요청서에 있던 요구사항이면 issueId 를 \
                    보내고, 개발방에서 새로 합의한 작업이면 issueId 없이 title 을 보낸다. 새 작업은 \
                    서버가 계약용 요구사항을 만들어 붙인다. 계약서에서 바꾼 이름·설명·확인 문장은 \
                    계약서에만 남고 요청서는 바뀌지 않는다.

                    **받는 요구사항은 요청서에 담긴 것과 이 계약서에 이미 들어 있는 것이다.** (R-REQ-05)

                    - 201: 계약서를 보냈을 때. 협의 중인 계약서가 있었으면 그 내용이 이걸로 바뀐다
                    - 400 DUE_DATE_BEFORE_START: dueDate 가 startDate 보다 이를 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 403 FORBIDDEN: 이 방의 개발자가 아닐 때. 계약서는 개발자만 쓴다
                    - 404 ROOM_NOT_FOUND: 없는 방이거나 이 방의 당사자가 아닐 때
                    - 409 NOT_DEVELOPMENT_ROOM: 개발방이 아닐 때
                    - 409 CONTRACT_ALREADY_SIGNED: 한쪽이라도 이미 서명했을 때 (R-CON-02)
                    - 409 CONTRACT_CANCELLED: 취소된 계약일 때 (R-CON-21)
                    - 422 INVALID_REQUEST: 요청 형식이 틀렸거나, 계약서에 넣을 수 없는 요구사항이 섞였을 때""")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/development-contracts")
    ContractResponse writeDevelopmentContract(
            @Parameter(description = "개발 방 식별자.", example = "93") @PathVariable Integer roomId,
            @Valid @RequestBody WriteDevelopmentContractRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return developmentContractWriteService.write(roomId, user.userId(), request);
    }
}
