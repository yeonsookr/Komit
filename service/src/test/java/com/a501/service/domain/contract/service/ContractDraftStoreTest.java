package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.contract.client.ContractDraftClient.GeneratedConsultingDraft;
import com.a501.service.domain.contract.entity.AiContractDraftSnapshot;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.repository.AiContractDraftSnapshotRepository;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContractDraftStoreTest {
    private static final int ROOM_ID = 92;
    private static final int CONTRACT_ID = 120;

    @Mock private ContractRepository contractRepository;
    @Mock private ConsultingAgreementRepository agreementRepository;
    @Mock private AiContractDraftSnapshotRepository snapshotRepository;

    private ContractDraftStore store;
    private GeneratedConsultingDraft draft;

    @BeforeEach
    void setUp() {
        store = new ContractDraftStore(contractRepository, agreementRepository, snapshotRepository);
        draft = new GeneratedConsultingDraft("인증 오류 확인", "재현 절차 확인", "{\"draft\":{}}");
    }

    @Test
    void savesNegotiatingContractSignedAgreementAndImmutableSnapshot() {
        Contract saved = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(saved, "id", CONTRACT_ID);
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.empty());
        when(contractRepository.save(any(Contract.class))).thenReturn(saved);

        assertThat(store.saveConsultingIfAbsent(ROOM_ID, draft)).isEqualTo(CONTRACT_ID);

        ArgumentCaptor<ConsultingAgreement> agreement =
                ArgumentCaptor.forClass(ConsultingAgreement.class);
        verify(agreementRepository).save(agreement.capture());
        assertThat(agreement.getValue().getStatus()).isEqualTo("SIGNED");
        assertThat(agreement.getValue().getTotalAmount()).isZero();
        assertThat(agreement.getValue().getTopic()).isEqualTo("인증 오류 확인");

        ArgumentCaptor<AiContractDraftSnapshot> snapshot =
                ArgumentCaptor.forClass(AiContractDraftSnapshot.class);
        verify(snapshotRepository).save(snapshot.capture());
        assertThat(snapshot.getValue().getContractId()).isEqualTo(CONTRACT_ID);
        assertThat(snapshot.getValue().getContentSnapshot()).isEqualTo("{\"draft\":{}}");
    }

    @Test
    void secondCheckReturnsExistingContractWithoutSavingDuplicates() {
        Contract existing = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(existing, "id", CONTRACT_ID);
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID))
                .thenReturn(Optional.of(existing));

        assertThat(store.saveConsultingIfAbsent(ROOM_ID, draft)).isEqualTo(CONTRACT_ID);
        verify(contractRepository, never()).save(any());
        verify(agreementRepository, never()).save(any());
        verify(snapshotRepository, never()).save(any());
    }
}
