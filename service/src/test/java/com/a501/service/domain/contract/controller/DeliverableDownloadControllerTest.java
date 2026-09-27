package com.a501.service.domain.contract.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.security.JwtTokenProvider;
import com.a501.service.common.security.WebMvcSecurityTest;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.service.DeliverableDownloadService;
import com.a501.service.domain.file.service.FileLookupService.FileContent;
import java.io.ByteArrayInputStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 인계 파일 내려받기의 경로, 헤더, 오류 본문이 목 명세와 같은지 본다. (R-QA-06) */
@WebMvcSecurityTest(DeliverableDownloadController.class)
class DeliverableDownloadControllerTest {
    private static final int USER_ID = 7;
    private static final byte[] BODY = {0x50, 0x4b, 0x03, 0x04};

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @MockitoBean private DeliverableDownloadService deliverableDownloadService;

    @Test
    void streamsTheMaterialAsAnAttachmentWithTheKoreanName() throws Exception {
        when(deliverableDownloadService.downloadMaterial(USER_ID, 501))
                .thenReturn(new FileContent("인계 소스.zip", (long) BODY.length, new ByteArrayInputStream(BODY)));

        send("/api/deliverable-materials/501/file")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(BODY.length)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.allOf(
                        Matchers.startsWith("attachment;"),
                        Matchers.containsString("filename*=UTF-8''%EC%9D%B8%EA%B3%84%20%EC%86%8C%EC%8A%A4.zip"))))
                .andExpect(content().bytes(BODY));
    }

    @Test
    void streamsTheLegacyFile() throws Exception {
        when(deliverableDownloadService.downloadLegacy(USER_ID, 401))
                .thenReturn(new FileContent("source.zip", (long) BODY.length, new ByteArrayInputStream(BODY)));

        send("/api/deliverables/401/file")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.containsString("filename=\"source.zip\"")))
                .andExpect(content().bytes(BODY));
    }

    @Test
    void answersLockedPaidFileWithTheMockBody() throws Exception {
        when(deliverableDownloadService.downloadMaterial(USER_ID, 502))
                .thenThrow(new BusinessException(ContractErrorCode.DELIVERABLE_ACCESS_LOCKED));
        when(deliverableDownloadService.downloadLegacy(USER_ID, 409))
                .thenThrow(new BusinessException(ContractErrorCode.DELIVERABLE_NOT_UPLOADED));

        send("/api/deliverable-materials/502/file")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERABLE_ACCESS_LOCKED"))
                .andExpect(jsonPath("$.message").value("대금 지급이 완료된 뒤에 내려받을 수 있습니다."));
        send("/api/deliverables/409/file")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERABLE_NOT_UPLOADED"))
                .andExpect(jsonPath("$.message").value("아직 올라온 파일이 없습니다."));
    }

    @Test
    void hidesMissingMaterials() throws Exception {
        when(deliverableDownloadService.downloadMaterial(USER_ID, 503))
                .thenThrow(new BusinessException(ContractErrorCode.DELIVERABLE_NOT_FOUND));

        send("/api/deliverable-materials/503/file")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELIVERABLE_NOT_FOUND"));
    }

    @Test
    void rejectsNonIntegerIdsAndMissingToken() throws Exception {
        send("/api/deliverable-materials/abc/file")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("deliverableMaterialId"))
                .andExpect(jsonPath("$.errors[0].reason").value("정수여야 합니다."));
        mockMvc.perform(get("/api/deliverable-materials/501/file")).andExpect(status().isUnauthorized());

        verifyNoInteractions(deliverableDownloadService);
    }

    private ResultActions send(String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.issue(USER_ID)));
    }
}
