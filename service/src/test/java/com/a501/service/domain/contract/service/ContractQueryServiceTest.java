package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ContractQueryServiceTest {
    private static final Integer AGREEMENT_ID = 11;
    private static final Integer CONTRACT_ID = 120;
    private static final Integer ROOM_ID = 92;
    private static final Integer CLIENT_ID = 7;
    private static final Integer DEVELOPER_ID = 9;

    private final ConsultingAgreementRepository agreementRepository =
            mock(ConsultingAgreementRepository.class);
    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final DevelopmentContractRepository developmentContractRepository =
            mock(DevelopmentContractRepository.class);
    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);

    private final ContractQueryService service =
            new ContractQueryService(
                    agreementRepository, contractRepository, developmentContractRepository, roomQueryService);

    @BeforeEach
    void setUp() {
        ConsultingAgreement agreement = BeanUtils.instantiateClass(ConsultingAgreement.class);
        ReflectionTestUtils.setField(agreement, "id", AGREEMENT_ID);
        ReflectionTestUtils.setField(agreement, "contractId", CONTRACT_ID);
        when(agreementRepository.findByIdAndDeletedFalse(AGREEMENT_ID))
                .thenReturn(Optional.of(agreement));

        Contract contract = BeanUtils.instantiateClass(Contract.class);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        ReflectionTestUtils.setField(contract, "roomId", ROOM_ID);
        when(contractRepository.findByIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(contract));

        when(roomQueryService.findParties(ROOM_ID))
                .thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_ID)));
    }

    @Test
    void 합의에서_계약과_두_당사자까지_내려간다() {
        var parties = service.findPartiesByConsultingAgreementId(AGREEMENT_ID);

        assertThat(parties).isPresent();
        assertThat(parties.get().contractId()).isEqualTo(CONTRACT_ID);
        assertThat(parties.get().clientId()).isEqualTo(CLIENT_ID);
        assertThat(parties.get().developerId()).isEqualTo(DEVELOPER_ID);
    }

    @Test
    void 두_당사자_모두_당사자로_본다() {
        var parties = service.findPartiesByConsultingAgreementId(AGREEMENT_ID).orElseThrow();

        assertThat(parties.includes(CLIENT_ID)).isTrue();
        assertThat(parties.includes(DEVELOPER_ID)).isTrue();
        assertThat(parties.includes(99)).isFalse();
    }

    /** 중간 어느 하나가 없으면 비어 있다. 무엇으로 답할지는 묻는 쪽이 정한다. */
    @Test
    void 합의가_없으면_비어_있다() {
        when(agreementRepository.findByIdAndDeletedFalse(AGREEMENT_ID))
                .thenReturn(Optional.empty());

        assertThat(service.findPartiesByConsultingAgreementId(AGREEMENT_ID)).isEmpty();
    }

    @Test
    void 방을_찾지_못하면_비어_있다() {
        when(roomQueryService.findParties(ROOM_ID)).thenReturn(Optional.empty());

        assertThat(service.findPartiesByConsultingAgreementId(AGREEMENT_ID)).isEmpty();
    }
}
