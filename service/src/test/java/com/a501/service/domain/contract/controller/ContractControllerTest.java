package com.a501.service.domain.contract.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.response.ConsultingAgreementResponse;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.dto.response.SignatureResponse;
import com.a501.service.domain.contract.dto.response.TempMessageResponse;
import com.a501.service.domain.contract.service.ConsultingContractWriteService;
import com.a501.service.domain.contract.service.ContractCancellationService;
import com.a501.service.domain.contract.service.ContractDepositService;
import com.a501.service.domain.contract.service.ContractDetailService;
import com.a501.service.domain.contract.service.ContractSignatureService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 목 명세 `GET /api/contracts/{contractId}` 와 같은 계약인지 봅니다. */
@WebMvcSecurityTest(ContractController.class)
class ContractControllerTest {
    private static final int USER_ID = 12;
    private static final int CONTRACT_ID = 120;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private ContractDetailService contractDetailService;
    @MockitoBean
    private ConsultingContractWriteService consultingContractWriteService;
    @MockitoBean
    private ContractSignatureService contractSignatureService;
    @MockitoBean
    private ContractDepositService contractDepositService;
    @MockitoBean
    private ContractCancellationService contractCancellationService;

    @Test
    void returnsTheContractWithOnlyOneBranchFilled() throws Exception {
        when(contractDetailService.getContract(USER_ID, CONTRACT_ID)).thenReturn(consultingContract());

        mockMvc.perform(get("/api/contracts/{contractId}", CONTRACT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"contractId":120,
                         "roomId":92,
                         "contractType":"CONSULTING",
                         "status":"NEGOTIATING",
                         "consulting":{"status":"SIGNED","totalAmount":0,
                                       "topic":"회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다",
                                       "detail":"회원탈퇴 처리 흐름을 화면 공유로 봅니다.",
                                       "sessions":[]},
                         "development":null,
                         "clientSignature":null,
                         "developerSignature":{"userId":12,"isSigned":false,
                                               "signedAt":null,"signatureImageFileId":null},
                         "messages":[{"messageId":31,"content":"금액을 어떻게 잡았는지 함께 적었습니다.",
                                      "isClient":false,"createdAt":"2026-08-22T11:40:00Z"}],
                         "contractText":null,
                         "updatedAt":"2026-08-22T11:40:00Z"}
                        """, true));
    }

    @Test
    void hidesSomeoneElsesContractAsNotFound() throws Exception {
        when(contractDetailService.getContract(USER_ID, CONTRACT_ID))
                .thenThrow(new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));

        mockMvc.perform(get("/api/contracts/{contractId}", CONTRACT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {"code":"CONTRACT_NOT_FOUND","message":"계약서를 찾을 수 없습니다."}
                        """, true));
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/contracts/{contractId}", CONTRACT_ID))
                .andExpect(status().isUnauthorized());

        verify(contractDetailService, never()).getContract(anyInt(), anyInt());
    }

    /** R-CON-21: 취소는 본문 없이 부르고 취소된 계약을 200 으로 돌려준다. */
    @Test
    void cancelsTheContractAndReturnsIt() throws Exception {
        when(contractCancellationService.cancel(CONTRACT_ID, USER_ID)).thenReturn(consultingContract());

        mockMvc.perform(post("/api/contracts/{contractId}/cancellation", CONTRACT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isOk());

        verify(contractCancellationService).cancel(CONTRACT_ID, USER_ID);
    }

    @Test
    void reportsPendingRefundAs503() throws Exception {
        when(contractCancellationService.cancel(CONTRACT_ID, USER_ID))
                .thenThrow(new BusinessException(ContractErrorCode.REFUND_PENDING));

        mockMvc.perform(post("/api/contracts/{contractId}/cancellation", CONTRACT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"code":"REFUND_PENDING",
                         "message":"계약을 취소했습니다. 환불을 확인하는 중이니 잠시 뒤 다시 눌러 주세요."}
                        """, true));
    }

    private ContractResponse consultingContract() {
        OffsetDateTime at = OffsetDateTime.of(2026, 8, 22, 11, 40, 0, 0, ZoneOffset.UTC);
        return new ContractResponse(
                CONTRACT_ID,
                92,
                "CONSULTING",
                "NEGOTIATING",
                new ConsultingAgreementResponse(
                        "SIGNED",
                        0L,
                        "회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다",
                        "회원탈퇴 처리 흐름을 화면 공유로 봅니다.",
                        List.of()),
                null,
                null,
                new SignatureResponse(USER_ID, false, null, null),
                List.of(
                        new TempMessageResponse(
                                31, "금액을 어떻게 잡았는지 함께 적었습니다.", false, at)),
                null,
                at);
    }
}
