package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.contract.dto.ConsultingPayoutTarget;
import com.a501.service.domain.contract.dto.ContractProgressView;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
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

    @Test
    void 지급_대상은_양측이_서명한_컨설팅_계약이다() {
        ConsultingAgreement agreement = ConsultingAgreement.of(CONTRACT_ID, "결제 오류 상담", 50_000);
        ReflectionTestUtils.setField(agreement, "id", AGREEMENT_ID);
        when(agreementRepository.findByStatusNotInAndDeletedFalse(List.of("COMPLETED", "TERMINATED")))
                .thenReturn(List.of(agreement));
        Contract contract = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        contract.sign(true, 1, null);
        contract.sign(false, 2, "계약 문구");
        when(contractRepository.findAllById(List.of(CONTRACT_ID))).thenReturn(List.of(contract));

        assertThat(service.findUnpaidConsultingContracts()).containsExactly(
                new ConsultingPayoutTarget(CONTRACT_ID, AGREEMENT_ID, 50_000, CLIENT_ID, DEVELOPER_ID));
    }

    @Test
    void 한쪽만_서명한_계약은_지급_대상이_아니다() {
        ConsultingAgreement agreement = ConsultingAgreement.of(CONTRACT_ID, "결제 오류 상담", 50_000);
        when(agreementRepository.findByStatusNotInAndDeletedFalse(List.of("COMPLETED", "TERMINATED")))
                .thenReturn(List.of(agreement));
        Contract contract = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        contract.sign(true, 1, null);
        when(contractRepository.findAllById(List.of(CONTRACT_ID))).thenReturn(List.of(contract));

        assertThat(service.findUnpaidConsultingContracts()).isEmpty();
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

    /**
     * R-CON-21: 취소된 계약은 되돌릴 수 없어 탈퇴를 막지 않는다. 개발 계약은 취소해도 상태가 그대로라 계약 상태로 본다.
     * R-CON-20: 의뢰인이 서명했으면 개발자 서명 전이라도 예치가 있을 수 있어, 취소됐어도 잔액은 따로 물어야 한다.
     */
    @Test
    void 취소된_계약은_마친_것이고_의뢰인이_서명했으면_예치가_있을_수_있다() {
        List<Integer> rooms = List.of(96, 97);
        Contract signedThenCancelled = contract(135, 97, "CANCELLED");
        ReflectionTestUtils.setField(signedThenCancelled, "clientSignatureId", 71);
        when(contractRepository.findByRoomIdInAndDeletedFalse(rooms)).thenReturn(List.of(
                contract(134, 96, "CANCELLED"), signedThenCancelled));
        List<Integer> ids = List.of(134, 135);
        Set<String> closed = Set.of("COMPLETED", "TERMINATED");
        when(agreementRepository.findByContractIdInAndDeletedFalseAndStatusIn(ids, closed)).thenReturn(List.of());
        when(developmentContractRepository.findByContractIdInAndDeletedFalseAndStatusIn(ids, closed)).thenReturn(List.of());

        assertThat(service.findProgressByRooms(rooms)).containsExactly(
                new ContractProgressView(134, 96, false, true),
                new ContractProgressView(135, 97, true, true));
    }

    /** R-CON-20: 의뢰인만 서명한 협의 중 계약은 아직 진행 중이지만 예치는 있을 수 있다. */
    @Test
    void 의뢰인만_서명한_협의_중_계약은_예치가_있을_수_있다() {
        Contract clientSigned = contract(136, 98, "NEGOTIATING");
        ReflectionTestUtils.setField(clientSigned, "clientSignatureId", 72);
        Contract developerSigned = contract(137, 99, "NEGOTIATING");
        ReflectionTestUtils.setField(developerSigned, "developerSignatureId", 73);
        List<Integer> rooms = List.of(98, 99);
        when(contractRepository.findByRoomIdInAndDeletedFalse(rooms)).thenReturn(List.of(clientSigned, developerSigned));
        List<Integer> ids = List.of(136, 137);
        Set<String> closed = Set.of("COMPLETED", "TERMINATED");
        when(agreementRepository.findByContractIdInAndDeletedFalseAndStatusIn(ids, closed)).thenReturn(List.of());
        when(developmentContractRepository.findByContractIdInAndDeletedFalseAndStatusIn(ids, closed)).thenReturn(List.of());

        assertThat(service.findProgressByRooms(rooms)).containsExactly(
                new ContractProgressView(136, 98, true, false),
                new ContractProgressView(137, 99, false, false));
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
