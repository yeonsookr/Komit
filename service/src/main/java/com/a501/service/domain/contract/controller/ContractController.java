package com.a501.service.domain.contract.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.contract.dto.request.AmendConsultingContractRequest;
import com.a501.service.domain.contract.dto.request.DepositRequest;
import com.a501.service.domain.contract.dto.request.SignContractRequest;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.dto.response.DepositResponse;
import com.a501.service.domain.contract.service.ConsultingContractWriteService;
import com.a501.service.domain.contract.service.ContractDepositService;
import com.a501.service.domain.contract.service.ContractDetailService;
import com.a501.service.domain.contract.service.ContractSignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "계약", description = "계약서 조회")
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {
    private final ContractDetailService contractDetailService;
    private final ConsultingContractWriteService consultingContractWriteService;
    private final ContractSignatureService contractSignatureService;
    private final ContractDepositService contractDepositService;

    @Operation(
            summary = "계약서 조회",
            description =
                    """
                    계약서 한 건을 돌려준다. 컨설팅과 개발이 계약 공통 부분을 함께 쓰고 갈래별 \
                    내용만 consulting 또는 development 한쪽에 담긴다. 나머지 한쪽은 항상 null 이다.

                    - 200: 계약서가 있고 내가 당사자일 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 CONTRACT_NOT_FOUND: 없는 계약이거나 요청자가 그 계약의 당사자가 아닐 때""")
    @GetMapping("/{contractId}")
    ContractResponse getContract(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer contractId) {
        return contractDetailService.getContract(user.userId(), contractId);
    }

    @Operation(
            summary = "계약서 수정 제안",
            description =
                    """
                    협의 중인 계약서를 고친다. **보낸 항목만 바뀌므로** 금액만 고치려고 나머지를 \
                    다시 적어 보내지 않아도 된다.

                    **한쪽이라도 서명한 뒤에는 고칠 수 없다.** 그때는 채팅으로 이야기한 뒤 다시 \
                    쓴다.

                    - 200: 고쳤을 때. 상태는 협의중으로 남는다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 CONTRACT_NOT_FOUND: 없는 계약이거나 요청자가 그 계약의 당사자가 아닐 때
                    - 409 CONTRACT_ALREADY_SIGNED: 이미 서명한 계약서일 때
                    - 422 INVALID_REQUEST: scheduledAt 이 지난 시각이거나 price 가 음수일 때""")
    @PatchMapping("/{contractId}")
    ContractResponse amendContract(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer contractId,
            @Valid @RequestBody AmendConsultingContractRequest request) {
        return consultingContractWriteService.amend(contractId, user.userId(), request);
    }

    @Operation(
            summary = "계약서 서명",
            description =
                    """
                    서명판에 그린 서명을 올린다. 그림은 먼저 파일로 올려 받은 식별자를 보낸다. \
                    서명판은 컨설팅과 개발이 같은 것을 쓴다.

                    **양쪽이 모두 서명해야 계약이 성립한다.** 한쪽만 서명한 동안에는 협의중으로 \
                    남는다. 양쪽이 마치면 그 시점의 계약 문구를 contractText 에 그대로 굳힌다 — \
                    나중에 양식이 바뀌어도 이 계약은 그때 합의한 글이다.

                    서명한 뒤에는 계약서를 고칠 수 없다.

                    - 201: 서명했을 때. 상대가 아직이면 계약은 협의중으로 남는다
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 CONTRACT_NOT_FOUND: 없는 계약이거나 요청자가 그 계약의 당사자가 아닐 때
                    - 409 ALREADY_SIGNED_BY_ME: 이미 내가 서명했을 때
                    - 422 INVALID_REQUEST: signatureImageFileId 가 없을 때""")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{contractId}/signatures")
    ContractResponse signContract(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer contractId,
            @Valid @RequestBody SignContractRequest request) {
        return contractSignatureService.sign(contractId, user.userId(), request);
    }

    @Operation(
            summary = "계약 금액 예치",
            description =
                    """
                    계약 금액을 Escrow 에 맡긴다. **돈의 원장은 이 서버가 갖지 않는다** — 결제와 \
                    보관은 Escrow 가 맡고 여기서는 걸어 달라고 부탁할 뿐이다.

                    **돈은 회원가입 때 발급한 의뢰인의 계좌에서 빠진다.** 계좌를 따로 고르지 \
                    않는다.

                    **예치는 의뢰인만 한다.** 돈을 내는 쪽이다. 방의 당사자는 계약이 있다는 것을 \
                    알고 있으므로 개발자에게는 감추지 않고 403 으로 답한다.

                    - 201: 예치를 걸었을 때. 결제가 바로 확인되면 DEPOSITED, 기다리는 중이면 PENDING
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 403 FORBIDDEN: 이 계약의 의뢰인이 아닐 때
                    - 404 CONTRACT_NOT_FOUND: 없는 계약이거나 요청자가 그 계약의 당사자가 아닐 때
                    - 409 ALREADY_DEPOSITED: 이미 예치했을 때
                    - 409 CONTRACT_NOT_SIGNED: 의뢰인이 아직 서명하지 않았을 때. 개발자 서명은 기다리지 않는다 (R-CON-20)
                    - 422 INVALID_REQUEST: paymentMethodId 나 idempotencyKey 가 없을 때""")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{contractId}/deposit")
    DepositResponse deposit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Integer contractId,
            @Valid @RequestBody DepositRequest request) {
        return contractDepositService.deposit(contractId, user.userId(), request);
    }

    @Operation(
            summary = "예치 상태 조회",
            description =
                    """
                    이 계약의 예치 상태를 읽는다. 상태는 Escrow 에 물어 그대로 옮긴다.

                    - 200: 예치를 건 적이 있을 때
                    - 401 UNAUTHORIZED: 로그인하지 않았을 때
                    - 404 CONTRACT_NOT_FOUND: 없는 계약이거나, 당사자가 아니거나, 아직 예치를 걸지 않았을 때""")
    @GetMapping("/{contractId}/deposit")
    DepositResponse getDeposit(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Integer contractId) {
        return contractDepositService.getDeposit(contractId, user.userId());
    }
}
