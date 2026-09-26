package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.contract.dto.request.ContractQaSuggestionRequest;
import com.a501.service.domain.diagnosis.client.QaSuggestionClient;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionListResponse;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ContractQaSuggestionServiceTest {
    private final ContractQaSuggestionReader reader = mock(ContractQaSuggestionReader.class);
    private final QaSuggestionClient client = mock(QaSuggestionClient.class);
    private ContractQaSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new ContractQaSuggestionService(reader, client);
    }

    @Test
    void passesPreparedRequirementToAiAndWrapsThreeCandidates() {
        ContractQaSuggestionRequest request =
                new ContractQaSuggestionRequest(null, "새 작업", "상세 설명");
        when(reader.read(93, 12, request)).thenReturn("새 작업\n상세 설명");
        when(client.suggest(any())).thenReturn(List.of(
                new QaSuggestionResponse("첫째"),
                new QaSuggestionResponse("둘째"),
                new QaSuggestionResponse("셋째")));

        QaSuggestionListResponse result = service.suggest(93, 12, request);

        verify(client).suggest("새 작업\n상세 설명");
        assertThat(result.content()).hasSize(3);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void doesNotKeepTransactionOpenAcrossAiCall() throws Exception {
        assertThat(ContractQaSuggestionService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(ContractQaSuggestionService.class
                        .getMethod(
                                "suggest",
                                Integer.class,
                                Integer.class,
                                ContractQaSuggestionRequest.class)
                        .getAnnotation(Transactional.class))
                .isNull();
    }
}
