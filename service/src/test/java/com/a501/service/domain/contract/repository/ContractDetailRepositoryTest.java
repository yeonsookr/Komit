package com.a501.service.domain.contract.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.config.JpaConfig;
import com.a501.service.domain.contract.entity.ConsultingAgreement;
import com.a501.service.domain.contract.entity.ContractSignature;
import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.entity.MilestoneQaResult;
import com.a501.service.domain.contract.entity.TempMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 계약서 조회가 쓰는 조회 메서드를 실제 PostgreSQL 18에서 검증합니다.
 * (BACKEND_CONVENTIONS 19 Repository 통합 테스트)
 *
 * <p>Spring Data 는 메서드 <b>이름</b>을 읽어 질의를 만듭니다. 이름이 어긋나도 컴파일은 통과하고
 * 기동할 때에야 터지므로, 단위 테스트로는 잡히지 않습니다. 소프트 삭제 제외와 정렬이 실제로
 * 적용되는지도 여기서 봅니다. H2 로 대체하지 않습니다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class ContractDetailRepositoryTest {
    private static final int CONTRACT_ID = 120;
    private static final int OTHER_CONTRACT_ID = 121;
    private static final int DEVELOPMENT_ID = 51;
    private static final int OTHER_DEVELOPMENT_ID = 52;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired private ConsultingAgreementRepository consultingAgreementRepository;
    @Autowired private DevelopmentContractRepository developmentContractRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private MilestoneQaResultRepository milestoneQaResultRepository;
    @Autowired private DeliverableRepository deliverableRepository;
    @Autowired private ContractSignatureRepository signatureRepository;
    @Autowired private TempMessageRepository tempMessageRepository;

    @Test
    @DisplayName("컨설팅 합의는 계약으로 찾고 삭제된 행은 빠진다")
    void findsConsultingAgreementByContract() {
        consultingAgreementRepository.deleteAll();
        consultingAgreementRepository.saveAndFlush(consultingAgreement(CONTRACT_ID, false));
        consultingAgreementRepository.saveAndFlush(consultingAgreement(OTHER_CONTRACT_ID, true));

        assertThat(consultingAgreementRepository.findByContractIdAndDeletedFalse(CONTRACT_ID))
                .hasValueSatisfying(found -> assertThat(found.getTopic()).isEqualTo("주제"));
        assertThat(consultingAgreementRepository.findByContractIdAndDeletedFalse(OTHER_CONTRACT_ID))
                .isEmpty();
    }

    @Test
    @DisplayName("개발 계약은 계약으로 찾고 삭제된 행은 빠진다")
    void findsDevelopmentContractByContract() {
        developmentContractRepository.deleteAll();
        developmentContractRepository.saveAndFlush(developmentContract(CONTRACT_ID, false));
        developmentContractRepository.saveAndFlush(developmentContract(OTHER_CONTRACT_ID, true));

        assertThat(developmentContractRepository.findByContractIdAndDeletedFalse(CONTRACT_ID))
                .hasValueSatisfying(found -> assertThat(found.getFreeRepairDays()).isEqualTo(14));
        assertThat(developmentContractRepository.findByContractIdAndDeletedFalse(OTHER_CONTRACT_ID))
                .isEmpty();
    }

    @Test
    @DisplayName("작업은 제 개발 계약 것만 순서대로 나오고 삭제된 행은 빠진다")
    void findsMilestonesOfOneDevelopmentContractInOrder() {
        milestoneRepository.deleteAll();
        Integer first = milestoneRepository.saveAndFlush(milestone(DEVELOPMENT_ID, false)).getId();
        Integer second = milestoneRepository.saveAndFlush(milestone(DEVELOPMENT_ID, false)).getId();
        milestoneRepository.saveAndFlush(milestone(DEVELOPMENT_ID, true));
        milestoneRepository.saveAndFlush(milestone(OTHER_DEVELOPMENT_ID, false));

        assertThat(
                        milestoneRepository
                                .findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
                                        DEVELOPMENT_ID)
                                .stream()
                                .map(Milestone::getId))
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("확인 문장 연결은 여러 작업을 한 번에 찾고 삭제된 행은 빠진다")
    void findsQaLinksForManyMilestonesAtOnce() {
        milestoneQaResultRepository.deleteAll();
        milestoneQaResultRepository.saveAndFlush(qaLink(301, 201, false));
        milestoneQaResultRepository.saveAndFlush(qaLink(302, 202, false));
        milestoneQaResultRepository.saveAndFlush(qaLink(302, 203, true));
        milestoneQaResultRepository.saveAndFlush(qaLink(303, 204, false));

        assertThat(
                        milestoneQaResultRepository
                                .findAllByMilestoneIdInAndDeletedFalseOrderByIdAsc(List.of(301, 302))
                                .stream()
                                .map(MilestoneQaResult::getQaCriterionId))
                .containsExactly(201, 202);
    }

    @Test
    @DisplayName("인계물은 제 개발 계약 것만 나오고 삭제된 행은 빠진다")
    void findsDeliverablesOfOneDevelopmentContract() {
        deliverableRepository.deleteAll();
        deliverableRepository.saveAndFlush(deliverable(DEVELOPMENT_ID, "소스 파일 전체", false));
        deliverableRepository.saveAndFlush(deliverable(DEVELOPMENT_ID, "지워진 인계물", true));
        deliverableRepository.saveAndFlush(deliverable(OTHER_DEVELOPMENT_ID, "남의 인계물", false));

        assertThat(
                        deliverableRepository
                                .findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
                                        DEVELOPMENT_ID)
                                .stream()
                                .map(Deliverable::getTitle))
                .containsExactly("소스 파일 전체");
    }

    @Test
    @DisplayName("서명은 살아 있는 행만 찾는다")
    void findsSignatureOnlyWhenAlive() {
        signatureRepository.deleteAll();
        Integer alive = signatureRepository.saveAndFlush(signature(false)).getId();
        Integer deleted = signatureRepository.saveAndFlush(signature(true)).getId();

        assertThat(signatureRepository.findByIdAndDeletedFalse(alive)).isPresent();
        assertThat(signatureRepository.findByIdAndDeletedFalse(deleted)).isEmpty();
    }

    @Test
    @DisplayName("전달 메시지는 제 계약 것만 남긴 순서대로 나온다")
    void findsTempMessagesOfOneContractInOrder() {
        tempMessageRepository.deleteAll();
        Integer first = tempMessageRepository.saveAndFlush(tempMessage(CONTRACT_ID, "먼저", false)).getId();
        Integer second =
                tempMessageRepository.saveAndFlush(tempMessage(CONTRACT_ID, "나중", false)).getId();
        tempMessageRepository.saveAndFlush(tempMessage(CONTRACT_ID, "지워짐", true));
        tempMessageRepository.saveAndFlush(tempMessage(OTHER_CONTRACT_ID, "남의 것", false));

        assertThat(
                        tempMessageRepository
                                .findAllByContractIdAndDeletedFalseOrderByIdAsc(CONTRACT_ID)
                                .stream()
                                .map(TempMessage::getId))
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("R-ACC-05: 완료·파기된 합의와 개발 계약만 골라내고, 진행 중이거나 삭제된 행은 빠진다")
    void findsFinishedByStatuses() {
        consultingAgreementRepository.deleteAll();
        developmentContractRepository.deleteAll();
        ConsultingAgreement completed = consultingAgreement(130, false);
        set(completed, "status", "COMPLETED");
        ConsultingAgreement terminated = consultingAgreement(131, false);
        set(terminated, "status", "TERMINATED");
        ConsultingAgreement deletedCompleted = consultingAgreement(132, true);
        set(deletedCompleted, "status", "COMPLETED");
        consultingAgreementRepository.saveAllAndFlush(
                List.of(completed, terminated, deletedCompleted, consultingAgreement(133, false)));
        DevelopmentContract devCompleted = developmentContract(134, false);
        set(devCompleted, "status", "COMPLETED");
        DevelopmentContract devInProgress = developmentContract(135, false);
        set(devInProgress, "status", "IN_PROGRESS");
        developmentContractRepository.saveAllAndFlush(List.of(devCompleted, devInProgress));
        List<Integer> contractIds = List.of(130, 131, 132, 133, 134, 135);
        List<String> closed = List.of("COMPLETED", "TERMINATED");

        assertThat(consultingAgreementRepository.findByContractIdInAndDeletedFalseAndStatusIn(contractIds, closed))
                .extracting(ConsultingAgreement::getContractId)
                .containsExactlyInAnyOrder(130, 131);
        assertThat(developmentContractRepository.findByContractIdInAndDeletedFalseAndStatusIn(contractIds, closed))
                .extracting(DevelopmentContract::getContractId)
                .containsExactly(134);
    }

    private ConsultingAgreement consultingAgreement(Integer contractId, boolean deleted) {
        ConsultingAgreement entity = BeanUtils.instantiateClass(ConsultingAgreement.class);
        set(entity, "contractId", contractId);
        set(entity, "topic", "주제");
        set(entity, "status", "SIGNED");
        set(entity, "statusGroup", "CONSULTING_AGREEMENT_STATUS");
        set(entity, "totalAmount", 0);
        markDeleted(entity, deleted);
        return entity;
    }

    private DevelopmentContract developmentContract(Integer contractId, boolean deleted) {
        DevelopmentContract entity = BeanUtils.instantiateClass(DevelopmentContract.class);
        set(entity, "contractId", contractId);
        set(entity, "status", "SIGNED");
        set(entity, "statusGroup", "DEVELOPMENT_CONTRACT_STATUS");
        set(entity, "totalAmount", 650000);
        set(entity, "freeRepairDays", 14);
        markDeleted(entity, deleted);
        return entity;
    }

    private Milestone milestone(Integer developmentContractId, boolean deleted) {
        Milestone entity = BeanUtils.instantiateClass(Milestone.class);
        set(entity, "developmentContractId", developmentContractId);
        set(entity, "price", 400000);
        set(entity, "status", "PROPOSED");
        set(entity, "statusGroup", "MILESTONE_STATUS");
        markDeleted(entity, deleted);
        return entity;
    }

    private MilestoneQaResult qaLink(Integer milestoneId, Integer qaCriterionId, boolean deleted) {
        MilestoneQaResult entity = BeanUtils.instantiateClass(MilestoneQaResult.class);
        set(entity, "milestoneId", milestoneId);
        set(entity, "qaCriterionId", qaCriterionId);
        markDeleted(entity, deleted);
        return entity;
    }

    private Deliverable deliverable(Integer developmentContractId, String title, boolean deleted) {
        Deliverable entity = BeanUtils.instantiateClass(Deliverable.class);
        set(entity, "developmentContractId", developmentContractId);
        set(entity, "title", title);
        markDeleted(entity, deleted);
        return entity;
    }

    private ContractSignature signature(boolean deleted) {
        ContractSignature entity = BeanUtils.instantiateClass(ContractSignature.class);
        set(entity, "userId", 12);
        markDeleted(entity, deleted);
        return entity;
    }

    private TempMessage tempMessage(Integer contractId, String content, boolean deleted) {
        TempMessage entity = BeanUtils.instantiateClass(TempMessage.class);
        set(entity, "contractId", contractId);
        set(entity, "content", content);
        markDeleted(entity, deleted);
        return entity;
    }

    private void set(Object entity, String field, Object value) {
        ReflectionTestUtils.setField(entity, field, value);
    }

    /** 소프트 삭제는 {@code is_deleted} 로만 가립니다. 조회 메서드가 그것을 보는지가 이 테스트의 대상입니다. */
    private void markDeleted(Object entity, boolean deleted) {
        if (deleted) {
            ReflectionTestUtils.setField(entity, "deleted", true, boolean.class);
        }
    }
}
