package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.contract.client.ContractDraftClient;
import com.a501.service.domain.contract.client.ContractDraftClient.GeneratedConsultingDraft;
import com.a501.service.domain.contract.dto.ContractDraftInput;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractDraftServiceTest {
    private static final int ROOM_ID = 92;
    private static final int USER_ID = 12;
    private static final int CONTRACT_ID = 120;

    @Mock private ContractDraftPreparationService preparationService;
    @Mock private ContractQueryService contractQueryService;
    @Mock private ContractDraftClient draftClient;
    @Mock private ContractDraftStore draftStore;
    @Mock private ContractDetailService contractDetailService;

    private ContractDraftService service;
    private ContractDraftInput input;
    private ContractResponse response;

    @BeforeEach
    void setUp() {
        service = new ContractDraftService(
                preparationService,
                contractQueryService,
                draftClient,
                draftStore,
                contractDetailService);
        input = new ContractDraftInput("CONSULTING", List.of());
        response = new ContractResponse(
                CONTRACT_ID, ROOM_ID, "CONSULTING", "NEGOTIATING",
                null, null, null, null, List.of(), null, null);
        when(preparationService.prepare(ROOM_ID, USER_ID)).thenReturn(input);
    }

    @Test
    void generatesStoresAndReturnsDraft() {
        GeneratedConsultingDraft generated =
                new GeneratedConsultingDraft("주제", "상세", "{\"draft\":{}}");
        when(contractQueryService.findContractIdByRoomId(ROOM_ID)).thenReturn(Optional.empty());
        when(draftClient.generateConsulting(input)).thenReturn(generated);
        when(draftStore.saveConsultingIfAbsent(ROOM_ID, generated)).thenReturn(CONTRACT_ID);
        when(contractDetailService.getContract(USER_ID, CONTRACT_ID)).thenReturn(response);

        assertThat(service.generate(ROOM_ID, USER_ID)).isSameAs(response);
        verify(draftClient).generateConsulting(input);
        verify(draftStore).saveConsultingIfAbsent(ROOM_ID, generated);
    }

    @Test
    void returnsSavedContractWithoutCallingAiAgain() {
        when(contractQueryService.findContractIdByRoomId(ROOM_ID))
                .thenReturn(Optional.of(CONTRACT_ID));
        when(contractDetailService.getContract(USER_ID, CONTRACT_ID)).thenReturn(response);

        assertThat(service.generate(ROOM_ID, USER_ID)).isSameAs(response);
        verify(draftClient, never()).generateConsulting(input);
        verify(draftStore, never()).saveConsultingIfAbsent(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
    }
}
