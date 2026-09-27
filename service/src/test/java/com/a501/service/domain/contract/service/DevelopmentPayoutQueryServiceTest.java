package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.contract.dto.DevelopmentPayoutTarget;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 지급할 개발 계약은 최종 인수 시험을 통과하고 아직 지급을 마치지 않은 계약이다. (R-PAY-01, R-QA-06) */
class DevelopmentPayoutQueryServiceTest {
    private static final int CONTRACT_ID = 130;
    private static final int DEVELOPMENT_ID = 51;
    private static final int ROOM_ID = 93;
    private static final int CLIENT_ID = 7;
    private static final int DEVELOPER_ID = 12;

    private final DevelopmentContractRepository developmentContractRepository = mock(DevelopmentContractRepository.class);
    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);

    private final DevelopmentPayoutQueryService service = new DevelopmentPayoutQueryService(
            developmentContractRepository, milestoneRepository, contractRepository, roomQueryService);

    private final List<Milestone> milestones = new ArrayList<>();
    private Contract contract;

    @BeforeEach
    void setUp() {
        DevelopmentContract development = DevelopmentContract.of(
                CONTRACT_ID, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14);
        ReflectionTestUtils.setField(development, "id", DEVELOPMENT_ID);
        development.changeTotalAmount(650_000);
        development.start();
        when(developmentContractRepository.findByStatusAndDeletedFalse("IN_PROGRESS")).thenReturn(List.of(development));
        when(milestoneRepository.findAllByDevelopmentContractIdInAndDeletedFalse(List.of(DEVELOPMENT_ID)))
                .thenReturn(milestones);
        contract = Contract.of(ROOM_ID, Contract.ContractType.DEVELOPMENT);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        ReflectionTestUtils.setField(contract, "status", "SIGNED");
        when(contractRepository.findAllById(List.of(CONTRACT_ID))).thenAnswer(invocation -> List.of(contract));
        when(roomQueryService.findParties(ROOM_ID)).thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_ID)));
    }

    @Test
    @DisplayName("인계 작업까지 모두 통과한 진행 중 계약을 총액과 두 당사자로 고른다")
    void picksContractsThatPassedFinalAcceptance() {
        milestones.addAll(List.of(milestone(false, "CONFIRMED"), milestone(true, "CONFIRMED")));

        assertThat(service.findUnpaidDevelopmentContracts()).containsExactly(
                new DevelopmentPayoutTarget(CONTRACT_ID, DEVELOPMENT_ID, 650_000, CLIENT_ID, DEVELOPER_ID));
    }

    @Test
    @DisplayName("인계 작업이 확정 전이면 고르지 않는다. 일반 작업만 모두 통과한 것은 최종 인수 시험 통과가 아니다")
    void skipsBeforeFinalAcceptance() {
        milestones.addAll(List.of(milestone(false, "CONFIRMED"), milestone(true, "SUBMITTED")));
        assertThat(service.findUnpaidDevelopmentContracts()).isEmpty();

        milestones.clear();
        milestones.add(milestone(false, "CONFIRMED"));
        assertThat(service.findUnpaidDevelopmentContracts()).isEmpty();
        verify(contractRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("통과하지 않은 일반 작업이 남아 있으면 고르지 않는다")
    void skipsWhenAnyWorkIsOpen() {
        milestones.addAll(List.of(milestone(false, "IN_PROGRESS"), milestone(true, "CONFIRMED")));

        assertThat(service.findUnpaidDevelopmentContracts()).isEmpty();
    }

    @Test
    @DisplayName("양쪽이 서명하지 않았거나 당사자를 찾지 못하면 고르지 않는다")
    void skipsUnconcludedOrPartyless() {
        milestones.addAll(List.of(milestone(false, "CONFIRMED"), milestone(true, "CONFIRMED")));
        ReflectionTestUtils.setField(contract, "status", "NEGOTIATING");
        assertThat(service.findUnpaidDevelopmentContracts()).isEmpty();

        ReflectionTestUtils.setField(contract, "status", "SIGNED");
        when(roomQueryService.findParties(ROOM_ID)).thenReturn(Optional.empty());
        assertThat(service.findUnpaidDevelopmentContracts()).isEmpty();
    }

    @Test
    @DisplayName("진행 중인 개발 계약이 없으면 작업을 묻지 않는다")
    void nothingInProgress() {
        when(developmentContractRepository.findByStatusAndDeletedFalse("IN_PROGRESS")).thenReturn(List.of());

        assertThat(service.findUnpaidDevelopmentContracts()).isEmpty();
        verify(milestoneRepository, never()).findAllByDevelopmentContractIdInAndDeletedFalse(any());
    }

    private static Milestone milestone(boolean handover, String status) {
        Milestone milestone = Milestone.of(DEVELOPMENT_ID, 312, null, null, handover ? 0 : 100000, null, handover);
        ReflectionTestUtils.setField(milestone, "status", status);
        return milestone;
    }
}
