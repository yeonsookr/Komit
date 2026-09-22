package com.a501.service.domain.contract.controller;

import com.a501.service.common.security.AuthenticatedUser;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.service.ContractDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "계약", description = "계약서 조회")
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {
    private final ContractDetailService contractDetailService;

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
}
