package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.a501.service.domain.contract.client.ContractDraftClient.CriteriaAddition;
import com.a501.service.domain.contract.client.ContractDraftClient.GeneratedConsultingDraft;
import com.a501.service.domain.contract.client.ContractDraftClient.GeneratedDevelopmentDraft;
import com.a501.service.domain.contract.client.ContractDraftClient.NewMilestone;
import com.a501.service.domain.contract.entity.AiContractDraftSnapshot;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.entity.MilestoneQaResult;
import com.a501.service.domain.contract.repository.AiContractDraftSnapshotRepository;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneQaResultRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.diagnosis.dto.DraftRequirements;
import com.a501.service.domain.diagnosis.service.ContractRequirementService;
import com.a501.service.domain.room.dto.ContractRoomView;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.ArrayList;
import java.util.List;
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
    @Mock private DevelopmentContractRepository developmentContractRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private MilestoneQaResultRepository milestoneQaResultRepository;
    @Mock private ContractRequirementService contractRequirementService;
    @Mock private RoomQueryService roomQueryService;

    private ContractDraftStore store;
    private GeneratedConsultingDraft draft;

    @BeforeEach
    void setUp() {
        store = new ContractDraftStore(
                contractRepository,
                agreementRepository,
                snapshotRepository,
                developmentContractRepository,
                milestoneRepository,
                milestoneQaResultRepository,
                contractRequirementService,
                roomQueryService);
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

    @Test
    void savesDevelopmentDraftFromTheRequestAndTheChat() {
        Contract saved = Contract.of(ROOM_ID, Contract.ContractType.DEVELOPMENT);
        ReflectionTestUtils.setField(saved, "id", CONTRACT_ID);
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.empty());
        when(contractRepository.save(any(Contract.class))).thenReturn(saved);
        when(roomQueryService.findContractRoom(ROOM_ID))
                .thenReturn(Optional.of(new ContractRoomView(ROOM_ID, "DEVELOPMENT", 8, new RoomParties(7, 41))));
        when(developmentContractRepository.save(any(DevelopmentContract.class))).thenAnswer(call -> {
            DevelopmentContract development = call.getArgument(0);
            ReflectionTestUtils.setField(development, "id", 51);
            return development;
        });
        List<Milestone> milestones = new ArrayList<>();
        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(call -> {
            Milestone milestone = call.getArgument(0);
            ReflectionTestUtils.setField(milestone, "id", 600 + milestones.size());
            milestones.add(milestone);
            return milestone;
        });
        when(contractRequirementService.createIssue(8, "관리자 화면에 회원 검색 추가", null)).thenReturn(320);
        when(contractRequirementService.createIssue(8, "주문 내역 내려받기", "엑셀 파일로 받는다")).thenReturn(321);
        when(contractRequirementService.writeCriteria(anyInt(), anyList())).thenReturn(List.of());
        when(contractRequirementService.writeCriteria(321, List.of("내려받은 파일이 엑셀에서 열린다")))
                .thenReturn(List.of(7001));
        List<DraftRequirements.Requested> requested = List.of(
                new DraftRequirements.Requested(312, "관리자 화면에 인증이 없다", null, List.of("로그인 화면이 뜬다")),
                new DraftRequirements.Requested(311, "비밀번호가 그대로 저장된다", null, List.of()));
        GeneratedDevelopmentDraft draft = new GeneratedDevelopmentDraft(
                List.of(new CriteriaAddition(312, List.of("일반 회원은 막힌다"))),
                List.of(
                        new NewMilestone("관리자 화면에 회원 검색 추가", null, List.of()),
                        new NewMilestone("주문 내역 내려받기", "엑셀 파일로 받는다", List.of("내려받은 파일이 엑셀에서 열린다"))),
                "{\"draft\":{},\"rejectedIssues\":[]}");

        assertThat(store.saveDevelopmentIfAbsent(ROOM_ID, requested, draft)).isEqualTo(CONTRACT_ID);

        // 요청서의 요구사항이 요청서 순서대로 먼저, 채팅에서 더한 새 작업이 뒤에 온다. 금액은 개발자가 채운다.
        assertThat(milestones).extracting(Milestone::getIssueId).containsExactly(312, 311, 320, 321);
        assertThat(milestones).allSatisfy(milestone -> {
            assertThat(milestone.getPrice()).isZero();
            assertThat(milestone.getTitle()).isNull();
            assertThat(milestone.getDevelopmentContractId()).isEqualTo(51);
        });
        // 요청서 문장을 옮기고 채팅에서 합의한 문장을 더한다. 합의한 문장이 없는 새 작업은 비어 있다.
        verify(contractRequirementService).writeCriteria(312, List.of("로그인 화면이 뜬다", "일반 회원은 막힌다"));
        verify(contractRequirementService).writeCriteria(311, List.of());
        verify(contractRequirementService).writeCriteria(320, List.of());
        verify(contractRequirementService).writeCriteria(321, List.of("내려받은 파일이 엑셀에서 열린다"));
        // 초안은 개발자가 아직 보내지 않은 것이라 요청서 요구사항의 상태를 바꾸지 않는다.
        verify(contractRequirementService, never()).markContracted(any());
        // 계약서 문장은 그 작업에 걸린다.
        ArgumentCaptor<MilestoneQaResult> link = ArgumentCaptor.forClass(MilestoneQaResult.class);
        verify(milestoneQaResultRepository).save(link.capture());
        assertThat(link.getValue().getMilestoneId()).isEqualTo(603);
        assertThat(link.getValue().getQaCriterionId()).isEqualTo(7001);
        ArgumentCaptor<AiContractDraftSnapshot> snapshot = ArgumentCaptor.forClass(AiContractDraftSnapshot.class);
        verify(snapshotRepository).save(snapshot.capture());
        assertThat(snapshot.getValue().getContentSnapshot()).contains("rejectedIssues");
        ArgumentCaptor<DevelopmentContract> development = ArgumentCaptor.forClass(DevelopmentContract.class);
        verify(developmentContractRepository).save(development.capture());
        assertThat(development.getValue().getStartDate()).isNull();
        assertThat(development.getValue().getStatus()).isEqualTo("SIGNED");
    }

    @Test
    void returnsExistingDevelopmentContractWithoutSaving() {
        Contract existing = Contract.of(ROOM_ID, Contract.ContractType.DEVELOPMENT);
        ReflectionTestUtils.setField(existing, "id", CONTRACT_ID);
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.of(existing));

        GeneratedDevelopmentDraft draft = new GeneratedDevelopmentDraft(List.of(), List.of(), "{}");
        assertThat(store.saveDevelopmentIfAbsent(ROOM_ID, List.of(), draft)).isEqualTo(CONTRACT_ID);
        verify(contractRepository, never()).save(any());
        verifyNoInteractions(contractRequirementService, milestoneRepository, snapshotRepository);
    }
}
