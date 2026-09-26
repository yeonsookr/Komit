package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.exception.ErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.WriteDevelopmentContractRequest;
import com.a501.service.domain.contract.dto.request.WriteMilestoneRequest;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.entity.MilestoneQaResult;
import com.a501.service.domain.contract.entity.TempMessage;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DeliverableRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneQaResultRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.contract.repository.TempMessageRepository;
import com.a501.service.domain.diagnosis.service.ContractRequirementService;
import com.a501.service.domain.room.RoomErrorCode;
import com.a501.service.domain.room.dto.ContractRoomView;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 개발 계약서를 쓰고 다시 쓰는 규칙을 DB 없이 검증한다.
 *
 * <p>방 하나에 계약서 하나이고, 한쪽이라도 서명한 뒤에는 고칠 수 없다(R-CON-02). 계약서에 담는 요구사항은
 * 요청서에 담긴 것과 이 계약서에 이미 들어 있는 것이다(R-REQ-05).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DevelopmentContractWriteServiceTest {
    private static final Integer ROOM_ID = 93;
    private static final Integer PROJECT_ID = 8;
    private static final Integer CONTRACT_ID = 130;
    private static final Integer DEVELOPMENT_ID = 51;
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;
    private static final Integer STRANGER_ID = 9999;
    private static final Integer NEW_ISSUE_ID = 320;

    @Mock private ContractRepository contractRepository;
    @Mock private DevelopmentContractRepository developmentContractRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private MilestoneQaResultRepository milestoneQaResultRepository;
    @Mock private DeliverableRepository deliverableRepository;
    @Mock private TempMessageRepository tempMessageRepository;
    @Mock private RoomQueryService roomQueryService;
    @Mock private ContractRequirementService contractRequirementService;
    @Mock private ContractDetailService contractDetailService;

    private DevelopmentContractWriteService service;
    private final List<Milestone> savedMilestones = new ArrayList<>();
    private final List<MilestoneQaResult> savedLinks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DevelopmentContractWriteService(
                contractRepository,
                developmentContractRepository,
                milestoneRepository,
                milestoneQaResultRepository,
                deliverableRepository,
                tempMessageRepository,
                new DevelopmentContractEditingPolicy(roomQueryService, contractRequirementService),
                contractRequirementService,
                contractDetailService);

        givenRoom("DEVELOPMENT");
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.empty());
        when(contractRepository.save(any(Contract.class))).thenAnswer(call -> {
            Contract saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", CONTRACT_ID);
            return saved;
        });
        when(developmentContractRepository.save(any(DevelopmentContract.class))).thenAnswer(call -> {
            DevelopmentContract saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", DEVELOPMENT_ID);
            return saved;
        });
        AtomicInteger ids = new AtomicInteger(600);
        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(call -> {
            Milestone saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ids.incrementAndGet());
            savedMilestones.add(saved);
            return saved;
        });
        when(milestoneQaResultRepository.save(any(MilestoneQaResult.class))).thenAnswer(call -> {
            savedLinks.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(contractRequirementService.findRequestIssueIds(PROJECT_ID)).thenReturn(Set.of(311, 312, 313));
        when(contractRequirementService.createIssue(eq(PROJECT_ID), anyString(), any())).thenReturn(NEW_ISSUE_ID);
        when(contractRequirementService.writeCriteria(anyInt(), anyList())).thenAnswer(call -> {
            List<String> lines = call.getArgument(1);
            int base = (Integer) call.getArgument(0) * 10;
            return java.util.stream.IntStream.range(0, lines.size()).mapToObj(i -> base + i).toList();
        });
    }

    @Test
    @DisplayName("처음 쓰면 계약과 개발 계약을 만든다. 계약은 협의 중, 개발 계약은 체결 상태다")
    void createsContractOnFirstWrite() {
        service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(existing(312))));

        ArgumentCaptor<Contract> contract = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).save(contract.capture());
        assertThat(contract.getValue().getRoomId()).isEqualTo(ROOM_ID);
        assertThat(contract.getValue().getContractType()).isEqualTo("DEVELOPMENT");
        assertThat(contract.getValue().getStatus()).isEqualTo("NEGOTIATING");

        DevelopmentContract development = savedDevelopment();
        assertThat(development.getContractId()).isEqualTo(CONTRACT_ID);
        assertThat(development.getStatus()).isEqualTo("SIGNED");
        assertThat(development.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(development.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(development.getFreeRepairDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("요청서의 요구사항은 그 번호에 붙이고, 계약서에서 바꾼 이름·설명은 작업에만 남긴다")
    void attachesExistingIssueWithRenamedTitle() {
        service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(
                new WriteMilestoneRequest(312, "관리자 인증 강화", "토큰도 확인한다", 400000, 10, List.of("로그인 화면이 뜬다")),
                existing(311))));

        assertThat(savedMilestones).extracting(Milestone::getIssueId).containsExactly(312, 311);
        assertThat(savedMilestones.get(0).getTitle()).isEqualTo("관리자 인증 강화");
        assertThat(savedMilestones.get(0).getDetail()).isEqualTo("토큰도 확인한다");
        assertThat(savedMilestones.get(1).getTitle()).isNull();
        assertThat(savedMilestones).allSatisfy(milestone -> {
            assertThat(milestone.getStatus()).isEqualTo("PROPOSED");
            assertThat(milestone.getDevelopmentContractId()).isEqualTo(DEVELOPMENT_ID);
        });
        verify(contractRequirementService, never()).createIssue(anyInt(), anyString(), any());
        verify(contractRequirementService).markContracted(List.of(312, 311));
    }

    @Test
    @DisplayName("새 작업은 계약용 요구사항을 먼저 만들어 붙이고, 이름·설명은 요구사항에 둔다")
    void createsIssueForNewWork() {
        service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(
                new WriteMilestoneRequest(null, "관리자 화면에 검색 추가", "회원을 찾는다", 200000, null, List.of("검색이 된다")))));

        verify(contractRequirementService).createIssue(PROJECT_ID, "관리자 화면에 검색 추가", "회원을 찾는다");
        assertThat(savedMilestones.getFirst().getIssueId()).isEqualTo(NEW_ISSUE_ID);
        assertThat(savedMilestones.getFirst().getTitle()).isNull();
        assertThat(savedMilestones.getFirst().getDetail()).isNull();
    }

    @Test
    @DisplayName("확인 문장은 계약서 문장으로 저장하고 작업에 건다")
    void linksContractCriteriaToMilestones() {
        service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(
                new WriteMilestoneRequest(312, null, null, 400000, null, List.of("로그인 화면이 뜬다", "관리자만 들어간다")))));

        verify(contractRequirementService).writeCriteria(312, List.of("로그인 화면이 뜬다", "관리자만 들어간다"));
        assertThat(savedLinks).extracting(MilestoneQaResult::getQaCriterionId).containsExactly(3120, 3121);
        assertThat(savedLinks).extracting(MilestoneQaResult::getMilestoneId)
                .containsOnly(savedMilestones.getFirst().getId());
    }

    @Test
    @DisplayName("계약 금액은 작업 가격의 합이다 (R-CON-04)")
    void totalsMilestonePrices() {
        service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(
                new WriteMilestoneRequest(312, null, null, 400000, null, List.of("a")),
                new WriteMilestoneRequest(311, null, null, 250000, null, List.of("b")),
                new WriteMilestoneRequest(null, "인계 · 소스 · 계정 · 설명서", null, 0, null, List.of("c")))));

        assertThat(savedDevelopment().getTotalAmount()).isEqualTo(650000);
    }

    @Test
    @DisplayName("인계물과 전달 메시지를 남긴다. 메시지가 비었으면 남기지 않는다")
    void savesDeliverablesAndMessage() {
        service.write(ROOM_ID, DEVELOPER_USER_ID, new WriteDevelopmentContractRequest(
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14,
                List.of(existing(312)), List.of("소스 파일 전체", "운영 설명서"), "  잘 부탁드립니다.  "));

        ArgumentCaptor<Deliverable> deliverables = ArgumentCaptor.forClass(Deliverable.class);
        verify(deliverableRepository, org.mockito.Mockito.times(2)).save(deliverables.capture());
        assertThat(deliverables.getAllValues()).extracting(Deliverable::getTitle)
                .containsExactly("소스 파일 전체", "운영 설명서");
        ArgumentCaptor<TempMessage> message = ArgumentCaptor.forClass(TempMessage.class);
        verify(tempMessageRepository).save(message.capture());
        assertThat(message.getValue().getContent()).isEqualTo("잘 부탁드립니다.");
        assertThat(message.getValue().isClient()).isFalse();
    }

    @Test
    @DisplayName("빈 메시지는 남기지 않는다")
    void skipsBlankMessage() {
        service.write(ROOM_ID, DEVELOPER_USER_ID, new WriteDevelopmentContractRequest(
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14, List.of(existing(312)), null, "   "));

        verify(tempMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("다시 쓰면 같은 계약을 이어 쓰고, 앞서 보낸 작업·계약서 문장·인계물을 지운다")
    void rewritesSameContract() {
        Contract contract = givenExistingContract();
        DevelopmentContract development = givenExistingDevelopment();
        Milestone previousWork = previousMilestone(701, 312);
        Deliverable previousDeliverable = Deliverable.of(DEVELOPMENT_ID, "옛 인계물");
        when(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(DEVELOPMENT_ID))
                .thenReturn(List.of(previousWork));
        when(milestoneQaResultRepository.findAllByMilestoneIdInAndDeletedFalseOrderByIdAsc(List.of(701)))
                .thenReturn(List.of(MilestoneQaResult.of(701, 5001), MilestoneQaResult.of(701, 5002)));
        when(deliverableRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(DEVELOPMENT_ID))
                .thenReturn(List.of(previousDeliverable));

        service.write(ROOM_ID, DEVELOPER_USER_ID, new WriteDevelopmentContractRequest(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 7, List.of(existing(312)), null, null));

        verify(contractRepository, never()).save(any());
        verify(developmentContractRepository, never()).save(any());
        assertThat(development.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(development.getFreeRepairDays()).isEqualTo(7);
        assertThat(previousWork.isDeleted()).isTrue();
        assertThat(previousDeliverable.isDeleted()).isTrue();
        verify(contractRequirementService).dropCriteria(List.of(5001, 5002));
        assertThat(contract.getStatus()).isEqualTo("NEGOTIATING");
    }

    @Test
    @DisplayName("다시 쓸 때는 이 계약서에 이미 든 계약용 요구사항도 받는다")
    void acceptsIssueAlreadyInContractOnRewrite() {
        givenExistingContract();
        givenExistingDevelopment();
        when(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(DEVELOPMENT_ID))
                .thenReturn(List.of(previousMilestone(701, NEW_ISSUE_ID)));

        service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(
                new WriteMilestoneRequest(NEW_ISSUE_ID, null, null, 0, null, List.of("소스를 받았다")))));

        assertThat(savedMilestones.getFirst().getIssueId()).isEqualTo(NEW_ISSUE_ID);
        verify(contractRequirementService, never()).createIssue(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("요청서에도 이 계약서에도 없는 요구사항이면 거부하고 아무것도 저장하지 않는다")
    void rejectsUnknownIssue() {
        assertCode(() -> service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(existing(312), existing(999)))),
                CommonErrorCode.INVALID_REQUEST);

        verify(contractRepository, never()).save(any());
        verify(milestoneRepository, never()).save(any());
        verify(contractRequirementService, never()).markContracted(any());
    }

    @Test
    @DisplayName("없는 방이거나 당사자가 아니면 방이 없다고 답한다")
    void unknownRoomOrStrangerIsNotFound() {
        assertCode(() -> service.write(ROOM_ID, STRANGER_ID, request(List.of(existing(312)))),
                RoomErrorCode.ROOM_NOT_FOUND);
        when(roomQueryService.findContractRoom(ROOM_ID)).thenReturn(Optional.empty());
        assertCode(() -> service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(existing(312)))),
                RoomErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("의뢰인은 계약서를 쓰지 않는다")
    void clientIsForbidden() {
        assertCode(() -> service.write(ROOM_ID, CLIENT_ID, request(List.of(existing(312)))),
                CommonErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("개발방이 아니면 거부한다")
    void rejectsNonDevelopmentRoom() {
        givenRoom("CONSULTING");

        assertCode(() -> service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(existing(312)))),
                ContractErrorCode.NOT_DEVELOPMENT_ROOM);
    }

    @Test
    @DisplayName("납기일이 착수일보다 이르면 거부한다. 같은 날은 받는다")
    void rejectsDueDateBeforeStart() {
        assertCode(() -> service.write(ROOM_ID, DEVELOPER_USER_ID, new WriteDevelopmentContractRequest(
                        LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 15), 14, List.of(existing(312)), null, null)),
                ContractErrorCode.DUE_DATE_BEFORE_START);

        service.write(ROOM_ID, DEVELOPER_USER_ID, new WriteDevelopmentContractRequest(
                LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 16), 14, List.of(existing(312)), null, null));
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    @DisplayName("한쪽이라도 서명한 계약서는 고칠 수 없다 (R-CON-02)")
    void rejectsSignedContract() {
        Contract contract = givenExistingContract();
        contract.sign(true, 77, null);

        assertCode(() -> service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(existing(312)))),
                ContractErrorCode.CONTRACT_ALREADY_SIGNED);
        verify(milestoneRepository, never()).save(any());
    }

    @Test
    @DisplayName("취소된 계약서는 고칠 수 없다 (R-CON-21)")
    void rejectsCancelledContract() {
        givenExistingContract().cancel();

        assertCode(() -> service.write(ROOM_ID, DEVELOPER_USER_ID, request(List.of(existing(312)))),
                ContractErrorCode.CONTRACT_CANCELLED);
    }

    private void givenRoom(String type) {
        when(roomQueryService.findContractRoom(ROOM_ID)).thenReturn(Optional.of(
                new ContractRoomView(ROOM_ID, type, PROJECT_ID, new RoomParties(CLIENT_ID, DEVELOPER_USER_ID))));
    }

    private Contract givenExistingContract() {
        Contract contract = Contract.of(ROOM_ID, Contract.ContractType.DEVELOPMENT);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.of(contract));
        return contract;
    }

    private DevelopmentContract givenExistingDevelopment() {
        DevelopmentContract development =
                DevelopmentContract.of(CONTRACT_ID, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14);
        ReflectionTestUtils.setField(development, "id", DEVELOPMENT_ID);
        when(developmentContractRepository.findByContractIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(development));
        return development;
    }

    private Milestone previousMilestone(int id, int issueId) {
        Milestone milestone = Milestone.of(DEVELOPMENT_ID, issueId, null, null, 100000, null);
        ReflectionTestUtils.setField(milestone, "id", id);
        return milestone;
    }

    private DevelopmentContract savedDevelopment() {
        ArgumentCaptor<DevelopmentContract> saved = ArgumentCaptor.forClass(DevelopmentContract.class);
        verify(developmentContractRepository).save(saved.capture());
        return saved.getValue();
    }

    private static WriteDevelopmentContractRequest request(List<WriteMilestoneRequest> milestones) {
        return new WriteDevelopmentContractRequest(
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14, milestones, null, null);
    }

    private static WriteMilestoneRequest existing(int issueId) {
        return new WriteMilestoneRequest(issueId, null, null, 400000, 10, List.of("로그인 화면이 뜬다"));
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
