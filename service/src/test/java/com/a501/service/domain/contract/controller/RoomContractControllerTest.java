package com.a501.service.domain.contract.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.service.ConsultingContractWriteService;
import com.a501.service.domain.contract.service.ContractDraftService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcSecurityTest(RoomContractController.class)
class RoomContractControllerTest {
    private static final int USER_ID = 12;
    private static final int ROOM_ID = 92;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @MockitoBean private ConsultingContractWriteService consultingContractWriteService;
    @MockitoBean private ContractDraftService contractDraftService;

    @Test
    void generatesDraftForAuthenticatedUser() throws Exception {
        ContractResponse response = new ContractResponse(
                120, ROOM_ID, "CONSULTING", "NEGOTIATING",
                null, null, null, null, List.of(), null, null);
        when(contractDraftService.generate(ROOM_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(post("/api/rooms/{roomId}/contract-draft", ROOM_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(120))
                .andExpect(jsonPath("$.status").value("NEGOTIATING"));

        verify(contractDraftService).generate(ROOM_ID, USER_ID);
    }

    @Test
    void rejectsDraftWithoutToken() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/contract-draft", ROOM_ID))
                .andExpect(status().isUnauthorized());

        verify(contractDraftService, never()).generate(anyInt(), anyInt());
    }

    @Test
    void rejectsConsultingMessageLongerThanDatabaseColumn() throws Exception {
        String body = """
                {"topic":"회원탈퇴 흐름 확인",
                 "scheduledAt":"2026-12-29T14:00:00+09:00",
                 "durationMinutes":30,
                 "price":50000,
                 "message":"%s"}
                """.formatted("가".repeat(301));

        mockMvc.perform(post("/api/rooms/{roomId}/consulting-contracts", ROOM_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("message"));

        verifyNoInteractions(consultingContractWriteService);
    }
}
