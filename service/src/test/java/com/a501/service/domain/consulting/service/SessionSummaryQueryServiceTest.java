package com.a501.service.domain.consulting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.consulting.ConsultingErrorCode;
import com.a501.service.domain.consulting.dto.SummaryContent;
import com.a501.service.domain.consulting.dto.response.SessionSummaryResponse;
import com.a501.service.domain.consulting.entity.ConsultingSession;
import com.a501.service.domain.consulting.repository.ConsultingSessionRepository;
import com.a501.service.domain.contract.dto.ContractParties;
import com.a501.service.domain.contract.service.ContractQueryService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SessionSummaryQueryServiceTest {
    private static final Integer SESSION_ID = 55;
    private static final Integer AGREEMENT_ID = 11;
    private static final LocalDateTime UPDATED = LocalDateTime.of(2026, 9, 25, 10, 0);

    private final ConsultingSessionRepository sessionRepository = mock(ConsultingSessionRepository.class);
    private final ContractQueryService contractQueryService = mock(ContractQueryService.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final SessionSummaryQueryService service =
            new SessionSummaryQueryService(sessionRepository, contractQueryService, objectMapper);

    private ConsultingSession session;

    @BeforeEach
    void setUp() {
        session = BeanUtils.instantiateClass(ConsultingSession.class);
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "consultingAgreementId", AGREEMENT_ID);
        ReflectionTestUtils.setField(session, "updatedAt", UPDATED);
        when(sessionRepository.findByIdAndDeletedFalse(SESSION_ID)).thenReturn(Optional.of(session));
        when(contractQueryService.findPartiesByConsultingAgreementId(AGREEMENT_ID))
                .thenReturn(Optional.of(new ContractParties(120, 7, 9)));
    }

    @Test
    void 저장된_요약에_세션과_수정_시각을_붙여_준다() {
        SummaryContent content = new SummaryContent(
                List.of(new SummaryContent.Item("논의", null)), List.of(), List.of(),
                List.of(new SummaryContent.Change(1, SummaryContent.Kind.ADD, null, "새 요구사항", null, false)));
        session.recordSummary(objectMapper.writeValueAsString(content));

        SessionSummaryResponse response = service.getSummary(7, SESSION_ID);

        assertThat(response).isEqualTo(SessionSummaryResponse.of(
                SESSION_ID, content, UPDATED.atOffset(ZoneOffset.UTC)));
    }

    @Test
    void 요약이_아직_없으면_만드는_중이다() {
        assertThatThrownBy(() -> service.getSummary(9, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SUMMARY_NOT_READY);
    }

    @Test
    void 당사자가_아니면_세션이_없는_것으로_답한다() {
        assertThatThrownBy(() -> service.getSummary(99, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ConsultingErrorCode.SESSION_NOT_FOUND);
    }
}
