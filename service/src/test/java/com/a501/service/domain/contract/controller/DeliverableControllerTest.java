package com.a501.service.domain.contract.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.AttachDeliverableFileRequest;
import com.a501.service.domain.contract.dto.response.DeliverableMaterialResponse;
import com.a501.service.domain.contract.dto.response.DeliverableResponse;
import com.a501.service.domain.contract.entity.DeliverableAccessStage;
import com.a501.service.domain.contract.service.DeliverableMaterialService;
import com.a501.service.domain.file.FileErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 인계 자료 등록 API의 요청 읽기, 상태 코드, 오류 본문이 목 명세와 같은지 본다. (R-QA-06) */
@WebMvcSecurityTest(DeliverableController.class)
class DeliverableControllerTest {
    private static final int USER_ID = 12;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @MockitoBean private DeliverableMaterialService deliverableMaterialService;

    @Test
    void attachesFileWithAccessStage() throws Exception {
        when(deliverableMaterialService.attachFile(eq(USER_ID), eq(401), any())).thenReturn(response());

        send("/api/deliverables/401/file", """
                {"fileId":3000000001,"accessStage":"PAID"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliverableId").value(401))
                .andExpect(jsonPath("$.materials[0].deliverableMaterialId").value(501))
                .andExpect(jsonPath("$.materials[0].accessStage").value("TEST"));

        ArgumentCaptor<AttachDeliverableFileRequest> sent = ArgumentCaptor.forClass(AttachDeliverableFileRequest.class);
        verify(deliverableMaterialService).attachFile(eq(USER_ID), eq(401), sent.capture());
        assertThat(sent.getValue().fileId()).isEqualTo(3_000_000_001L);
        assertThat(sent.getValue().accessStage()).isEqualTo(DeliverableAccessStage.PAID);
    }

    @Test
    void rejectsUnknownAccessStageLikeTheMock() throws Exception {
        send("/api/deliverables/401/file", """
                {"fileId":71,"accessStage":"READY"}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("accessStage"))
                .andExpect(jsonPath("$.errors[0].reason").value("'TEST' or 'PAID' 중 하나여야 합니다."));

        verifyNoInteractions(deliverableMaterialService);
    }

    @Test
    void rejectsMissingFields() throws Exception {
        send("/api/deliverables/401/file", "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(2));

        verifyNoInteractions(deliverableMaterialService);
    }

    @Test
    void rejectsNonHttpUrl() throws Exception {
        send("/api/deliverables/401/urls", """
                {"url":"javascript:alert(1)"}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("url"))
                .andExpect(jsonPath("$.errors[0].reason").value("http 또는 https 주소여야 합니다."));

        verifyNoInteractions(deliverableMaterialService);
    }

    @Test
    void rejectsUrlLongerThan2000CharactersLikeTheMock() throws Exception {
        String base = "https://demo.example.com/";
        String longUrl = base + "a".repeat(2001 - base.length());

        send("/api/deliverables/401/urls", "{\"url\":\"" + longUrl + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("url"))
                .andExpect(jsonPath("$.errors[0].reason").value("2000자 이하여야 합니다."));

        verifyNoInteractions(deliverableMaterialService);
    }

    @Test
    void addsUrl() throws Exception {
        when(deliverableMaterialService.addUrl(eq(USER_ID), eq(401), any())).thenReturn(response());

        send("/api/deliverables/401/urls", """
                {"url":"https://demo.example.com"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isContractual").value(true));
    }

    @Test
    void mapsDomainErrors() throws Exception {
        when(deliverableMaterialService.attachFile(eq(USER_ID), eq(401), any()))
                .thenThrow(new BusinessException(ContractErrorCode.HANDOVER_NOT_OPEN))
                .thenThrow(new BusinessException(ContractErrorCode.MATERIAL_STAGE_CONFLICT))
                .thenThrow(new BusinessException(FileErrorCode.FILE_NOT_ATTACHABLE))
                .thenThrow(new BusinessException(ContractErrorCode.DELIVERABLE_NOT_FOUND))
                .thenThrow(new BusinessException(ContractErrorCode.HANDOVER_UNDER_REVIEW));
        String body = """
                {"fileId":71,"accessStage":"TEST"}""";

        send("/api/deliverables/401/file", body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_NOT_OPEN"))
                .andExpect(jsonPath("$.message").value("일반 작업을 모두 마친 뒤에 인계 자료를 등록할 수 있습니다."));
        send("/api/deliverables/401/file", body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_STAGE_CONFLICT"));
        send("/api/deliverables/401/file", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_NOT_ATTACHABLE"));
        send("/api/deliverables/401/file", body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("인계물을 찾을 수 없습니다."));
        send("/api/deliverables/401/file", body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_UNDER_REVIEW"))
                .andExpect(jsonPath("$.message").value("최종 인수 시험 중에는 인계 자료를 바꿀 수 없습니다."));
    }

    @Test
    void rejectsWithoutToken() throws Exception {
        mockMvc.perform(post("/api/deliverables/401/file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":71,\"accessStage\":\"TEST\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(deliverableMaterialService);
    }

    private ResultActions send(String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static DeliverableResponse response() {
        return new DeliverableResponse(401, "소스 파일 전체", null, null, true, List.of(
                new DeliverableMaterialResponse(501, null, "https://demo.example.com", "TEST", null)));
    }
}
