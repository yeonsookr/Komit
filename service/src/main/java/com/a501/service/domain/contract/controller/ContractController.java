package com.a501.service.domain.contract.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.contract.dto.request.AmendConsultingContractRequest;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.service.ConsultingContractWriteService;
import com.a501.service.domain.contract.service.ContractDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "계약", description = "계약서 조회")
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {
    private final ContractDetailService contractDetailService;
    private final ConsultingContractWriteService consultingContractWriteService;

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
}
