package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.consulting.service.ConsultingSessionQueryService;
import com.a501.service.domain.contract.dto.response.ContractResponse;
import com.a501.service.domain.contract.dto.response.MilestoneResponse;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.entity.MilestoneQaResult;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.ContractSignatureRepository;
import com.a501.service.domain.contract.repository.DeliverableRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneQaResultRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.contract.repository.TempMessageRepository;
import com.a501.service.domain.diagnosis.dto.IssueBrief;
import com.a501.service.domain.diagnosis.dto.response.QaCriterionResponse;
import com.a501.service.domain.diagnosis.service.DiagnosisQueryService;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 계약서 조회가 흩어진 테이블을 어떻게 모으는지 봅니다.
 *
 * <p>작업 이름·위치는 요구사항에서, 확인 문장은 {@code milestone_qa_results} 를 거쳐, 자동 통과
 * 시각은 확인 요청 시각에서 나옵니다. 그 셋이 이 조회의 계약입니다.
 */
class ContractDetailServiceTest {
    private static final int CLIENT_ID = 12;
    private static final int DEVELOPER_ID = 9;
    private static final int STRANGER_ID = 77;
    private static final int CONTRACT_ID = 120;
    private static final int ROOM_ID = 105;
    private static final int DEVELOPMENT_ID = 51;
    private static final int MILESTONE_ID = 301;
    private static final int ISSUE_ID = 312;
    private static final int QA_CRITERION_ID = 201;

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final ConsultingAgreementRepository consultingAgreementRepository =
            mock(ConsultingAgreementRepository.class);
    private final DevelopmentContractRepository developmentContractRepository =
            mock(DevelopmentContractRepository.class);
    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private final MilestoneQaResultRepository milestoneQaResultRepository =
            mock(MilestoneQaResultRepository.class);
    private final DeliverableRepository deliverableRepository = mock(DeliverableRepository.class);
    private final ContractSignatureRepository signatureRepository =
            mock(ContractSignatureRepository.class);
    private final TempMessageRepository tempMessageRepository = mock(TempMessageRepository.class);
    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);
    private final ConsultingSessionQueryService sessionQueryService =
            mock(ConsultingSessionQueryService.class);
    private final DiagnosisQueryService diagnosisQueryService = mock(DiagnosisQueryService.class);

    private final ContractDetailService service =
            new ContractDetailService(
                    contractRepository,
                    consultingAgreementRepository,
                    developmentContractRepository,
                    milestoneRepository,
                    milestoneQaResultRepository,
                    deliverableRepository,
                    signatureRepository,
                    tempMessageRepository,
                    roomQueryService,
                    sessionQueryService,
                    diagnosisQueryService);

    @BeforeEach
    void stubCommonLookups() {
        when(contractRepository.findByIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(developmentContract()));
        when(roomQueryService.findParties(ROOM_ID))
                .thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_ID)));
        when(developmentContractRepository.findByContractIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(development()));
        when(deliverableRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
                        DEVELOPMENT_ID))
                .thenReturn(List.of());
        when(tempMessageRepository.findAllByContractIdAndDeletedFalseOrderByIdAsc(CONTRACT_ID))
                .thenReturn(List.of());
    }

    @Test
    void takesWorkTitleAndLocationFromTheIssueItIsAttachedTo() {
        givenOneMilestone(null);
        when(diagnosisQueryService.findBriefs(List.of(ISSUE_ID)))
                .thenReturn(Map.of(ISSUE_ID, new IssueBrief("관리자 API에 인증 추가", "app/api/admin/*")));

        MilestoneResponse milestone = firstMilestone(service.getContract(CLIENT_ID, CONTRACT_ID));

        assertThat(milestone.title()).isEqualTo("관리자 API에 인증 추가");
        assertThat(milestone.location()).isEqualTo("app/api/admin/*");
    }

    @Test
    void collectsIssueIdsFromTheMilestones() {
        givenOneMilestone(null);
        when(diagnosisQueryService.findBriefs(any())).thenReturn(Map.of());

        ContractResponse response = service.getContract(CLIENT_ID, CONTRACT_ID);

        assertThat(response.development().issueIds()).containsExactly(ISSUE_ID);
    }

    @Test
    void linksQaCriteriaThroughTheMilestoneResults() {
        givenOneMilestone(null);
        when(diagnosisQueryService.findBriefs(any())).thenReturn(Map.of());
        when(milestoneQaResultRepository.findAllByMilestoneIdInAndDeletedFalseOrderByIdAsc(
                        List.of(MILESTONE_ID)))
                .thenReturn(List.of(qaLink()));
        when(diagnosisQueryService.findQaCriteria(List.of(QA_CRITERION_ID)))
                .thenReturn(
                        Map.of(
                                QA_CRITERION_ID,
                                new QaCriterionResponse(
                                        QA_CRITERION_ID,
                                        "관리자 주소로 들어가면 로그인 화면이 뜬다",
                                        null,
                                        "UNRESOLVED",
                                        ISSUE_ID)));

        MilestoneResponse milestone = firstMilestone(service.getContract(CLIENT_ID, CONTRACT_ID));

        assertThat(milestone.qaCriteria()).hasSize(1);
        assertThat(milestone.qaCriteria().getFirst().testCase())
                .isEqualTo("관리자 주소로 들어가면 로그인 화면이 뜬다");
    }

    /** R-QA-02: 확인 요청 후 3일이 지나면 자동 통과된다. */
    @Test
    void autoPassIsThreeDaysAfterTheWorkWasSubmitted() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 8, 29, 11, 20);
        givenOneMilestone(submittedAt);
        when(diagnosisQueryService.findBriefs(any())).thenReturn(Map.of());

        MilestoneResponse milestone = firstMilestone(service.getContract(CLIENT_ID, CONTRACT_ID));

        assertThat(milestone.autoPassAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 1, 11, 20).atOffset(ZoneOffset.UTC));
    }

    @Test
    void noAutoPassBeforeSubmissionOrAfterConfirmation() {
        givenOneMilestone(null);
        when(diagnosisQueryService.findBriefs(any())).thenReturn(Map.of());
        assertThat(firstMilestone(service.getContract(CLIENT_ID, CONTRACT_ID)).autoPassAt()).isNull();

        Milestone confirmed = milestone(LocalDateTime.of(2026, 8, 24, 10, 0));
        ReflectionTestUtils.setField(confirmed, "confirmedAt", LocalDateTime.of(2026, 8, 26, 16, 40));
        when(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
                        DEVELOPMENT_ID))
                .thenReturn(List.of(confirmed));

        assertThat(firstMilestone(service.getContract(CLIENT_ID, CONTRACT_ID)).autoPassAt()).isNull();
    }

    @Test
    void usesTheStoredTotalAmountWithoutRecomputing() {
        givenOneMilestone(null);
        when(diagnosisQueryService.findBriefs(any())).thenReturn(Map.of());

        ContractResponse response = service.getContract(CLIENT_ID, CONTRACT_ID);

        assertThat(response.development().totalAmount()).isEqualTo(650000L);
    }

    @Test
    void includesTheStoredConsultingDetailInTheConsultingBranch() {
        Contract contract = BeanUtils.instantiateClass(Contract.class);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        ReflectionTestUtils.setField(contract, "roomId", ROOM_ID);
        ReflectionTestUtils.setField(contract, "contractType", "CONSULTING");
        ReflectionTestUtils.setField(contract, "status", "NEGOTIATING");
        ConsultingAgreement agreement = ConsultingAgreement.of(
                CONTRACT_ID,
                "회원탈퇴 경로를 봅니다",
                50_000,
                "탈퇴 데이터 보관 방법을 확인합니다");
        ReflectionTestUtils.setField(agreement, "id", 7);

        when(contractRepository.findByIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(contract));
        when(consultingAgreementRepository.findByContractIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(agreement));
        when(sessionQueryService.findByConsultingAgreementId(7, CONTRACT_ID))
                .thenReturn(List.of());

        ContractResponse response = service.getContract(CLIENT_ID, CONTRACT_ID);

        assertThat(response.consulting().detail()).isEqualTo("탈퇴 데이터 보관 방법을 확인합니다");
        assertThat(response.development()).isNull();
    }

    @Test
    void theDeveloperSeesTheSameContract() {
        givenOneMilestone(null);
        when(diagnosisQueryService.findBriefs(any())).thenReturn(Map.of());

        assertThat(service.getContract(DEVELOPER_ID, CONTRACT_ID).contractId()).isEqualTo(CONTRACT_ID);
    }

    @Test
    void hidesTheContractFromSomeoneWhoIsNotAParty() {
        assertThatThrownBy(() -> service.getContract(STRANGER_ID, CONTRACT_ID))
                .isInstanceOf(BusinessException.class);

        verify(milestoneRepository, never())
                .findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(anyInt());
    }

    @Test
    void missingContractIsNotFound() {
        when(contractRepository.findByIdAndDeletedFalse(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getContract(CLIENT_ID, 404))
                .isInstanceOf(BusinessException.class);
    }

    private void givenOneMilestone(LocalDateTime submittedAt) {
        when(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
                        DEVELOPMENT_ID))
                .thenReturn(List.of(milestone(submittedAt)));
        when(milestoneQaResultRepository.findAllByMilestoneIdInAndDeletedFalseOrderByIdAsc(any()))
                .thenReturn(List.of());
    }

    private MilestoneResponse firstMilestone(ContractResponse response) {
        return response.development().milestones().getFirst();
    }

    private Contract developmentContract() {
        Contract contract = BeanUtils.instantiateClass(Contract.class);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        ReflectionTestUtils.setField(contract, "roomId", ROOM_ID);
        ReflectionTestUtils.setField(contract, "contractType", "DEVELOPMENT");
        ReflectionTestUtils.setField(contract, "status", "NEGOTIATING");
        return contract;
    }

    private DevelopmentContract development() {
        DevelopmentContract development = BeanUtils.instantiateClass(DevelopmentContract.class);
        ReflectionTestUtils.setField(development, "id", DEVELOPMENT_ID);
        ReflectionTestUtils.setField(development, "contractId", CONTRACT_ID);
        ReflectionTestUtils.setField(development, "status", "PRE_SIGNED");
        ReflectionTestUtils.setField(development, "totalAmount", 650000);
        ReflectionTestUtils.setField(development, "freeRepairDays", 14);
        return development;
    }

    private Milestone milestone(LocalDateTime submittedAt) {
        Milestone milestone = BeanUtils.instantiateClass(Milestone.class);
        ReflectionTestUtils.setField(milestone, "id", MILESTONE_ID);
        ReflectionTestUtils.setField(milestone, "developmentContractId", DEVELOPMENT_ID);
        ReflectionTestUtils.setField(milestone, "issueId", ISSUE_ID);
        ReflectionTestUtils.setField(milestone, "price", 400000);
        ReflectionTestUtils.setField(milestone, "status", "SUBMITTED");
        ReflectionTestUtils.setField(milestone, "submittedAt", submittedAt);
        return milestone;
    }

    private MilestoneQaResult qaLink() {
        MilestoneQaResult link = BeanUtils.instantiateClass(MilestoneQaResult.class);
        ReflectionTestUtils.setField(link, "milestoneId", MILESTONE_ID);
        ReflectionTestUtils.setField(link, "qaCriterionId", QA_CRITERION_ID);
        return link;
    }
}
