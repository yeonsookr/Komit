package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.dto.ContractProgressView;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /** R-ACC-05: 협의 중인 계약도 마치지 않은 것이고, 상담 합의·개발 계약이 완료·파기돼야 마친 것이다. */
    @Test
    void 협의_중도_진행_중이고_완료와_파기만_마친_것으로_본다() {
        List<Integer> rooms = List.of(92, 93, 94, 95);
        when(contractRepository.findByRoomIdInAndDeletedFalse(rooms)).thenReturn(List.of(
                contract(133, 95, "SIGNED"),
                contract(131, 93, "NEGOTIATING"),
                contract(130, 92, "SIGNED"),
                contract(132, 94, "SIGNED")));
        List<Integer> ids = List.of(133, 131, 130, 132);
        Set<String> closed = Set.of("COMPLETED", "TERMINATED");
        when(agreementRepository.findByContractIdInAndDeletedFalseAndStatusIn(ids, closed))
                .thenReturn(List.of(agreement(133)));
        when(developmentContractRepository.findByContractIdInAndDeletedFalseAndStatusIn(ids, closed))
                .thenReturn(List.of(developmentContract(132)));

        assertThat(service.findProgressByRooms(rooms)).containsExactly(
                new ContractProgressView(130, 92, true, false),
                new ContractProgressView(131, 93, false, false),
                new ContractProgressView(132, 94, true, true),
                new ContractProgressView(133, 95, true, true));
    }

    @Test
    void 방이_없으면_계약을_묻지_않는다() {
        assertThat(service.findProgressByRooms(List.of())).isEmpty();

        verify(contractRepository, never()).findByRoomIdInAndDeletedFalse(any());
    }

    private static Contract contract(Integer id, Integer roomId, String status) {
        Contract contract = BeanUtils.instantiateClass(Contract.class);
        ReflectionTestUtils.setField(contract, "id", id);
        ReflectionTestUtils.setField(contract, "roomId", roomId);
        ReflectionTestUtils.setField(contract, "status", status);
        return contract;
    }

    private static ConsultingAgreement agreement(Integer contractId) {
        ConsultingAgreement agreement = BeanUtils.instantiateClass(ConsultingAgreement.class);
        ReflectionTestUtils.setField(agreement, "contractId", contractId);
        return agreement;
    }

    private static DevelopmentContract developmentContract(Integer contractId) {
        DevelopmentContract developmentContract = BeanUtils.instantiateClass(DevelopmentContract.class);
        ReflectionTestUtils.setField(developmentContract, "contractId", contractId);
        return developmentContract;
    }
}
