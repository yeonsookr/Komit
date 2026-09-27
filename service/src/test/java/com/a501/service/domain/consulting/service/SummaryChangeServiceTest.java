package com.a501.service.domain.consulting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.dto.SummaryContent;
import com.a501.service.domain.consulting.dto.response.SessionSummaryResponse;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.diagnosis.service.RequirementService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SummaryChangeServiceTest {
    private static final Integer SESSION_ID = 55;
    private static final Integer AGREEMENT_ID = 11;
    private static final Integer PROJECT_ID = 8;
    private static final Integer CLIENT = 7;
    private static final Integer DEVELOPER = 9;

    private final ConsultingSessionRepository sessionRepository = mock(ConsultingSessionRepository.class);
    private final ContractQueryService contractQueryService = mock(ContractQueryService.class);
    private final RequirementService requirementService = mock(RequirementService.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final SummaryChangeService service =
            new SummaryChangeService(sessionRepository, contractQueryService, requirementService, objectMapper);

    private ConsultingSession session;

    @BeforeEach
    void setUp() {
        session = BeanUtils.instantiateClass(ConsultingSession.class);
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "consultingAgreementId", AGREEMENT_ID);
        ReflectionTestUtils.setField(session, "updatedAt", LocalDateTime.of(2026, 9, 27, 18, 0));
        when(sessionRepository.findWithLockByIdAndDeletedFalse(SESSION_ID)).thenReturn(Optional.of(session));
        when(contractQueryService.findPartiesByConsultingAgreementId(AGREEMENT_ID))
                .thenReturn(Optional.of(new ContractParties(120, CLIENT, DEVELOPER)));
        when(contractQueryService.findProjectIdByConsultingAgreementId(AGREEMENT_ID))
                .thenReturn(Optional.of(PROJECT_ID));
    }

    @Test
    void 고른_후보만_요청서에_넣고_반영됨으로_표시한다() {
        summarize(add(1, "결제 실패 로그 수집", "재현 로그를 모읍니다."), add(2, "CORS 설정", null));

        SessionSummaryResponse response = service.apply(CLIENT, SESSION_ID, List.of(1));

        verify(requirementService).addToRequest(PROJECT_ID, "결제 실패 로그 수집", "재현 로그를 모읍니다.");
        verify(requirementService, never()).addToRequest(PROJECT_ID, "CORS 설정", null);
        assertThat(response.changes()).extracting(SummaryContent.Change::isApplied).containsExactly(true, false);
        assertThat(stored().changes()).extracting(SummaryContent.Change::isApplied).containsExactly(true, false);
    }

    @Test
    void 이미_반영했거나_요약에_없는_후보는_건너뛴다() {
        summarize(new SummaryContent.Change(1, SummaryContent.Kind.ADD, null, "결제 실패 로그 수집", null, true));

        SessionSummaryResponse response = service.apply(CLIENT, SESSION_ID, List.of(1, 99));

        verify(requirementService, never()).addToRequest(any(), any(), any());
        assertThat(response.changes()).extracting(SummaryContent.Change::isApplied).containsExactly(true);
    }

    @Test
    void 개발자는_반영할_수_없다() {
        summarize(add(1, "결제 실패 로그 수집", null));

        assertThatThrownBy(() -> service.apply(DEVELOPER, SESSION_ID, List.of(1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.FORBIDDEN);
        verify(requirementService, never()).addToRequest(any(), any(), any());
    }

    @Test
    void 당사자가_아니면_세션이_없는_것으로_답한다() {
        assertThatThrownBy(() -> service.apply(99, SESSION_ID, List.of(1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void 요약이_아직_없으면_만드는_중이다() {
        assertThatThrownBy(() -> service.apply(CLIENT, SESSION_ID, List.of(1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SUMMARY_NOT_READY);
    }

    @Test
    void 기존_요구사항을_고치는_후보는_받지_않는다() {
        summarize(
                add(1, "결제 실패 로그 수집", null),
                new SummaryContent.Change(2, SummaryContent.Kind.UPDATE, 312, "검수 기준 갱신", null, false));

        assertThatThrownBy(() -> service.apply(CLIENT, SESSION_ID, List.of(1, 2)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_REQUEST);
        verify(requirementService, never()).addToRequest(any(), any(), any());
    }

    private static SummaryContent.Change add(int changeId, String title, String detail) {
        return new SummaryContent.Change(changeId, SummaryContent.Kind.ADD, null, title, detail, false);
    }

    private void summarize(SummaryContent.Change... changes) {
        session.recordSummary(objectMapper.writeValueAsString(
                new SummaryContent(List.of(), List.of(), List.of(), List.of(changes))));
    }

    private SummaryContent stored() {
        return objectMapper.readValue(session.getSummary(), SummaryContent.class);
    }
}
