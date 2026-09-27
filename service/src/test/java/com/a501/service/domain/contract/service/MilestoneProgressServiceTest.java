package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.exception.ErrorCode;
import com.a501.service.common.exception.InvalidFieldException;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.ConfirmMilestoneRequest;
import com.a501.service.domain.contract.dto.request.SaveQaResultsRequest;
import com.a501.service.domain.contract.dto.response.MilestoneResponse;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.entity.MilestoneQaResult;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneQaResultRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.report.service.ReportQueryService;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/** 확인 요청·확인 결과·통과·자동 통과의 조건과 상태 전이를 본다. (R-QA-01~03, R-QA-06) */
class MilestoneProgressServiceTest {
    private static final int CONTRACT_ID = 130;
    private static final int DEVELOPMENT_ID = 51;
    private static final int ROOM_ID = 93;
    private static final int CLIENT_ID = 7;
    private static final int DEVELOPER_ID = 12;
    private static final int STRANGER_ID = 99;
    private static final int FIRST_ID = 301;
    private static final int SECOND_ID = 302;
    private static final int HANDOVER_ID = 303;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 10, 0);

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final DevelopmentContractRepository developmentContractRepository = mock(DevelopmentContractRepository.class);
    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private final MilestoneQaResultRepository qaResultRepository = mock(MilestoneQaResultRepository.class);
    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);
    private final ReportQueryService reportQueryService = mock(ReportQueryService.class);
    private final DeliverableMaterialService deliverableMaterialService = mock(DeliverableMaterialService.class);
    private final ContractDetailService contractDetailService = mock(ContractDetailService.class);

    private final MilestoneProgressService service = new MilestoneProgressService(
            contractRepository, developmentContractRepository, milestoneRepository, qaResultRepository,
            roomQueryService, reportQueryService, deliverableMaterialService, contractDetailService,
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private DevelopmentContract development;
    private Milestone first;
    private Milestone second;
    private Milestone handover;
    private final Map<Integer, List<MilestoneQaResult>> results = new HashMap<>();

    @BeforeEach
    void setUp() {
        Contract contract = Contract.of(ROOM_ID, Contract.ContractType.DEVELOPMENT);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        ReflectionTestUtils.setField(contract, "status", "SIGNED");
        when(contractRepository.findWithLockByIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.of(contract));
        when(roomQueryService.findParties(ROOM_ID)).thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_ID)));

        development = DevelopmentContract.of(CONTRACT_ID, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14);
        ReflectionTestUtils.setField(development, "id", DEVELOPMENT_ID);
        development.start();
        when(developmentContractRepository.findByIdAndDeletedFalse(DEVELOPMENT_ID)).thenReturn(Optional.of(development));

        first = milestone(FIRST_ID, false, "IN_PROGRESS");
        second = milestone(SECOND_ID, false, "SCHEDULED");
        handover = milestone(HANDOVER_ID, true, "SCHEDULED");
        when(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(DEVELOPMENT_ID))
                .thenAnswer(invocation -> List.of(first, second, handover));
        for (Milestone milestone : List.of(first, second, handover)) {
            when(milestoneRepository.findContractIdByMilestoneId(milestone.getId())).thenReturn(Optional.of(CONTRACT_ID));
            when(milestoneRepository.findByIdAndDeletedFalse(milestone.getId())).thenReturn(Optional.of(milestone));
        }
        results.put(FIRST_ID, List.of(result(FIRST_ID, 201), result(FIRST_ID, 202)));
        results.put(SECOND_ID, List.of(result(SECOND_ID, 203)));
        results.put(HANDOVER_ID, List.of(result(HANDOVER_ID, 204)));
        when(qaResultRepository.findAllByMilestoneIdAndDeletedFalseOrderByIdAsc(any()))
                .thenAnswer(invocation -> results.getOrDefault(invocation.<Integer>getArgument(0), List.of()));
        when(contractDetailService.getMilestone(any()))
                .thenAnswer(invocation -> MilestoneResponse.of(invocation.getArgument(0), null, List.of()));
    }

    // ------------------------------------------------------------------ 확인 요청

    @Test
    @DisplayName("개발자가 확인을 요청하면 SUBMITTED 가 되고 지금부터 3일 뒤가 자동 통과 시각이다")
    void submitStartsTheAutoPassClock() {
        MilestoneResponse response = service.submit(FIRST_ID, DEVELOPER_ID);

        assertThat(first.getStatus()).isEqualTo("SUBMITTED");
        assertThat(first.getSubmittedAt()).isEqualTo(NOW);
        assertThat(response.autoPassAt()).isEqualTo(NOW.plusDays(3).atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("착수 뒤 뒤로 잡힌 일반 작업도 순서와 관계없이 확인을 요청한다")
    void scheduledWorkCanBeSubmittedOutOfOrder() {
        service.submit(SECOND_ID, DEVELOPER_ID);

        assertThat(second.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("다시 요청하면 자동 통과 기한을 다시 센다")
    void resubmitRestartsTheClock() {
        ReflectionTestUtils.setField(first, "status", "SUBMITTED");
        ReflectionTestUtils.setField(first, "submittedAt", NOW.minusDays(2));

        service.submit(FIRST_ID, DEVELOPER_ID);

        assertThat(first.getSubmittedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("통과한 작업이나 착수 전 계약의 작업은 확인을 요청하지 못한다")
    void submitNeedsUnderwayWork() {
        ReflectionTestUtils.setField(first, "status", "CONFIRMED");
        assertCode(() -> service.submit(FIRST_ID, DEVELOPER_ID), ContractErrorCode.MILESTONE_STATE_CONFLICT);

        ReflectionTestUtils.setField(second, "status", "PROPOSED");
        assertCode(() -> service.submit(SECOND_ID, DEVELOPER_ID), ContractErrorCode.MILESTONE_STATE_CONFLICT);

        ReflectionTestUtils.setField(development, "status", "SIGNED");
        ReflectionTestUtils.setField(second, "status", "IN_PROGRESS");
        assertCode(() -> service.submit(SECOND_ID, DEVELOPER_ID), ContractErrorCode.MILESTONE_STATE_CONFLICT);
        assertThat(second.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("인계 작업은 일반 작업을 모두 통과하기 전에는 요청하지 못하고 자료도 보지 않는다")
    void handoverWaitsForEveryRegularWork() {
        ReflectionTestUtils.setField(first, "status", "CONFIRMED");

        assertCode(() -> service.submit(HANDOVER_ID, DEVELOPER_ID), ContractErrorCode.HANDOVER_NOT_OPEN);

        assertThat(handover.getStatus()).isEqualTo("SCHEDULED");
        verify(deliverableMaterialService, never()).requireFinalAcceptanceMaterials(any());
    }

    @Test
    @DisplayName("인계 작업은 인수 시험 자료와 PAID 파일이 없으면 요청하지 못한다")
    void handoverNeedsFinalAcceptanceMaterials() {
        confirmRegularWork();
        doThrow(new BusinessException(ContractErrorCode.HANDOVER_MATERIALS_MISSING))
                .when(deliverableMaterialService).requireFinalAcceptanceMaterials(development);

        assertCode(() -> service.submit(HANDOVER_ID, DEVELOPER_ID), ContractErrorCode.HANDOVER_MATERIALS_MISSING);

        assertThat(handover.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("일반 작업을 모두 통과하고 자료가 있으면 인계 작업의 확인을 요청한다")
    void handoverIsSubmittedWhenReady() {
        confirmRegularWork();

        service.submit(HANDOVER_ID, DEVELOPER_ID);

        assertThat(handover.getStatus()).isEqualTo("SUBMITTED");
        verify(deliverableMaterialService).requireFinalAcceptanceMaterials(development);
    }

    @Test
    @DisplayName("일반 작업은 인계 자료를 보지 않는다")
    void regularWorkDoesNotLookAtMaterials() {
        service.submit(FIRST_ID, DEVELOPER_ID);

        verify(deliverableMaterialService, never()).requireFinalAcceptanceMaterials(any());
    }

    @Test
    @DisplayName("계약 행을 잠근 뒤에 작업을 읽는다")
    void locksTheContractBeforeReadingTheWork() {
        service.submit(FIRST_ID, DEVELOPER_ID);

        InOrder order = inOrder(contractRepository, milestoneRepository);
        order.verify(contractRepository).findWithLockByIdAndDeletedFalse(CONTRACT_ID);
        order.verify(milestoneRepository).findByIdAndDeletedFalse(FIRST_ID);
        // 잠그기 전에 한 번 더 읽으면 그 엔티티가 영속성 컨텍스트에 남아 잠근 뒤에도 옛 상태를 본다.
        verify(milestoneRepository).findByIdAndDeletedFalse(FIRST_ID);
    }

    @Test
    @DisplayName("확인 요청은 개발자만, 결과와 통과는 의뢰인만 한다. 당사자가 아니거나 없는 작업은 없다고 답한다")
    void rolesAndStrangers() {
        assertCode(() -> service.submit(FIRST_ID, CLIENT_ID), CommonErrorCode.FORBIDDEN);
        assertCode(() -> service.saveQaResults(FIRST_ID, DEVELOPER_ID, save(201)), CommonErrorCode.FORBIDDEN);
        assertCode(() -> service.confirm(FIRST_ID, DEVELOPER_ID, confirm(201, 202)), CommonErrorCode.FORBIDDEN);
        assertCode(() -> service.submit(FIRST_ID, STRANGER_ID), ContractErrorCode.MILESTONE_NOT_FOUND);
        when(milestoneRepository.findContractIdByMilestoneId(404)).thenReturn(Optional.empty());
        assertCode(() -> service.submit(404, DEVELOPER_ID), ContractErrorCode.MILESTONE_NOT_FOUND);

        assertThat(first.getStatus()).isEqualTo("IN_PROGRESS");
    }

    // ------------------------------------------------------------------ 확인 결과 저장

    @Test
    @DisplayName("확인 요청 중에 일부만 통과시키면 작업이 개발자에게 돌아가고 자동 통과를 멈춘다")
    void partialResultReturnsTheWork() {
        submitted(first);

        MilestoneResponse response = service.saveQaResults(FIRST_ID, CLIENT_ID, save(201));

        assertThat(results.get(FIRST_ID)).extracting(MilestoneQaResult::isPassed).containsExactly(true, false);
        assertThat(first.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(first.getSubmittedAt()).isNull();
        assertThat(response.autoPassAt()).isNull();
    }

    @Test
    @DisplayName("전부 통과시켜도 확인 결과 저장은 완료가 아니다. 확인 요청 중이면 그대로 둔다")
    void fullResultKeepsTheSubmission() {
        submitted(first);

        service.saveQaResults(FIRST_ID, CLIENT_ID, save(201, 202));

        assertThat(results.get(FIRST_ID)).extracting(MilestoneQaResult::isPassed).containsExactly(true, true);
        assertThat(first.getStatus()).isEqualTo("SUBMITTED");
        assertThat(first.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("체크는 더하기만 한다. 빼고 보내도 이미 통과한 것은 풀리지 않는다")
    void resultsAreAdditive() {
        results.get(FIRST_ID).getFirst().pass();

        service.saveQaResults(FIRST_ID, CLIENT_ID, save(202));

        assertThat(results.get(FIRST_ID)).extracting(MilestoneQaResult::isPassed).containsExactly(true, true);
    }

    @Test
    @DisplayName("확인 요청 전 작업에 결과를 남겨도 상태는 그대로다")
    void resultsBeforeSubmissionKeepTheStatus() {
        service.saveQaResults(FIRST_ID, CLIENT_ID, save(201));
        service.saveQaResults(SECOND_ID, CLIENT_ID, save(203));

        assertThat(first.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(second.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("다른 작업의 확인 문장이 섞이면 필드 오류로 거부하고 아무것도 남기지 않는다")
    void resultsRejectOtherCriteria() {
        submitted(first);

        assertThatThrownBy(() -> service.saveQaResults(FIRST_ID, CLIENT_ID, save(201, 203)))
                .isInstanceOfSatisfying(InvalidFieldException.class, exception -> {
                    assertThat(exception.getField()).isEqualTo("qaCriterionIds");
                    assertThat(exception.getReason()).isEqualTo("이 작업의 확인 문장만 보낼 수 있습니다.");
                });

        assertThat(results.get(FIRST_ID)).extracting(MilestoneQaResult::isPassed).containsExactly(false, false);
        assertThat(first.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("통과한 작업의 결과는 바꾸지 못한다")
    void resultsOfConfirmedWorkAreFinal() {
        ReflectionTestUtils.setField(first, "status", "CONFIRMED");

        assertCode(() -> service.saveQaResults(FIRST_ID, CLIENT_ID, save(201)), ContractErrorCode.MILESTONE_STATE_CONFLICT);
    }

    // ------------------------------------------------------------------ 통과

    @Test
    @DisplayName("확인 문장을 모두 체크하면 통과하고 되돌리지 않는다")
    void confirmPassesEveryCriterion() {
        submitted(first);

        service.confirm(FIRST_ID, CLIENT_ID, confirm(201, 202));

        assertThat(first.getStatus()).isEqualTo("CONFIRMED");
        assertThat(first.getConfirmedAt()).isEqualTo(NOW);
        assertThat(results.get(FIRST_ID)).extracting(MilestoneQaResult::isPassed).containsExactly(true, true);
        assertCode(() -> service.confirm(FIRST_ID, CLIENT_ID, confirm(201, 202)), ContractErrorCode.MILESTONE_STATE_CONFLICT);
    }

    @Test
    @DisplayName("하나라도 빠지거나 빈 목록이면 통과하지 않고 아무것도 남기지 않는다")
    void confirmNeedsEveryCriterion() {
        submitted(first);

        assertCode(() -> service.confirm(FIRST_ID, CLIENT_ID, confirm(201)), ContractErrorCode.QA_NOT_ALL_CHECKED);
        assertCode(() -> service.confirm(FIRST_ID, CLIENT_ID, confirm()), ContractErrorCode.QA_NOT_ALL_CHECKED);

        assertThat(first.getStatus()).isEqualTo("SUBMITTED");
        assertThat(results.get(FIRST_ID)).extracting(MilestoneQaResult::isPassed).containsExactly(false, false);
    }

    @Test
    @DisplayName("다른 작업의 확인 문장이 섞이면 통과하지 않는다")
    void confirmRejectsOtherCriteria() {
        submitted(first);

        assertThatThrownBy(() -> service.confirm(FIRST_ID, CLIENT_ID, confirm(201, 202, 203)))
                .isInstanceOf(InvalidFieldException.class);

        assertThat(first.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("확인 요청이 오기 전에는 통과시키지 못한다")
    void confirmNeedsSubmission() {
        assertCode(() -> service.confirm(FIRST_ID, CLIENT_ID, confirm(201, 202)), ContractErrorCode.MILESTONE_STATE_CONFLICT);

        assertThat(first.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("인계 작업을 통과시키면 최종 인수 시험 통과다")
    void confirmingHandoverPassesFinalAcceptance() {
        confirmRegularWork();
        submitted(handover);

        service.confirm(HANDOVER_ID, CLIENT_ID, confirm(204));

        assertThat(handover.getStatus()).isEqualTo("CONFIRMED");
    }

    // ------------------------------------------------------------------ 자동 통과

    @Test
    @DisplayName("요청하고 3일이 지나면 자동 통과하고 확인 문장도 모두 통과로 남긴다")
    void autoPassesAfterThreeDays() {
        submittedAt(first, NOW.minusDays(3));

        assertThat(service.autoPass(FIRST_ID, NOW)).isTrue();

        assertThat(first.getStatus()).isEqualTo("CONFIRMED");
        assertThat(first.getConfirmedAt()).isEqualTo(NOW);
        assertThat(results.get(FIRST_ID)).extracting(MilestoneQaResult::isPassed).containsExactly(true, true);
        verify(contractRepository).findWithLockByIdAndDeletedFalse(CONTRACT_ID);
    }

    @Test
    @DisplayName("3일이 되기 전에는 자동 통과하지 않는다")
    void noAutoPassBeforeTheDeadline() {
        submittedAt(first, NOW.minusDays(3).plusSeconds(1));

        assertThat(service.autoPass(FIRST_ID, NOW)).isFalse();

        assertThat(first.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("해결되지 않은 신고가 있으면 자동 통과하지 않는다")
    void noAutoPassWithOpenReport() {
        submittedAt(first, NOW.minusDays(4));
        when(reportQueryService.hasOpenReport(CONTRACT_ID)).thenReturn(true);

        assertThat(service.autoPass(FIRST_ID, NOW)).isFalse();

        assertThat(first.getStatus()).isEqualTo("SUBMITTED");
        assertThat(results.get(FIRST_ID)).extracting(MilestoneQaResult::isPassed).containsExactly(false, false);
    }

    @Test
    @DisplayName("잠근 뒤 보니 이미 답을 받았거나 통과했거나 계약이 진행 중이 아니면 건너뛴다")
    void autoPassRechecksAfterLocking() {
        submittedAt(first, NOW.minusDays(4));
        first.returnToWork();
        assertThat(service.autoPass(FIRST_ID, NOW)).isFalse();
        assertThat(first.getStatus()).isEqualTo("IN_PROGRESS");

        submittedAt(second, NOW.minusDays(4));
        second.confirm(NOW.minusDays(1));
        assertThat(service.autoPass(SECOND_ID, NOW)).isFalse();
        assertThat(second.getConfirmedAt()).isEqualTo(NOW.minusDays(1));

        submittedAt(handover, NOW.minusDays(4));
        ReflectionTestUtils.setField(development, "status", "COMPLETED");
        assertThat(service.autoPass(HANDOVER_ID, NOW)).isFalse();
        assertThat(handover.getStatus()).isEqualTo("SUBMITTED");

        when(milestoneRepository.findContractIdByMilestoneId(404)).thenReturn(Optional.empty());
        assertThat(service.autoPass(404, NOW)).isFalse();
    }

    @Test
    @DisplayName("인계 작업도 같은 기한으로 자동 통과한다")
    void handoverAutoPassesToo() {
        confirmRegularWork();
        submittedAt(handover, NOW.minusDays(3));

        assertThat(service.autoPass(HANDOVER_ID, NOW)).isTrue();

        assertThat(handover.getStatus()).isEqualTo("CONFIRMED");
    }

    private void confirmRegularWork() {
        ReflectionTestUtils.setField(first, "status", "CONFIRMED");
        ReflectionTestUtils.setField(second, "status", "CONFIRMED");
    }

    private static void submitted(Milestone milestone) {
        submittedAt(milestone, NOW.minusHours(1));
    }

    private static void submittedAt(Milestone milestone, LocalDateTime submittedAt) {
        ReflectionTestUtils.setField(milestone, "status", "SUBMITTED");
        ReflectionTestUtils.setField(milestone, "submittedAt", submittedAt);
    }

    private static SaveQaResultsRequest save(Integer... qaCriterionIds) {
        return new SaveQaResultsRequest(List.of(qaCriterionIds));
    }

    private static ConfirmMilestoneRequest confirm(Integer... qaCriterionIds) {
        return new ConfirmMilestoneRequest(List.of(qaCriterionIds));
    }

    private static Milestone milestone(int id, boolean handover, String status) {
        Milestone milestone = Milestone.of(DEVELOPMENT_ID, 312, null, null, handover ? 0 : 100000, null, handover);
        ReflectionTestUtils.setField(milestone, "id", id);
        ReflectionTestUtils.setField(milestone, "status", status);
        return milestone;
    }

    private static MilestoneQaResult result(int milestoneId, int qaCriterionId) {
        return MilestoneQaResult.of(milestoneId, qaCriterionId);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
