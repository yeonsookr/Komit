package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.common.exception.ErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.AddDeliverableRequest;
import com.a501.service.domain.contract.dto.request.AddDeliverableUrlRequest;
import com.a501.service.domain.contract.dto.request.AttachDeliverableFileRequest;
import com.a501.service.domain.contract.dto.response.DeliverableResponse;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DeliverableAccessStage;
import com.a501.service.domain.contract.entity.DeliverableMaterial;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DeliverableMaterialRepository;
import com.a501.service.domain.contract.repository.DeliverableRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.file.FileErrorCode;
import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** 인계 자료 등록의 열리는 때·권한·중복·단계 충돌을 본다. (R-QA-06) */
class DeliverableMaterialServiceTest {
    private static final int CONTRACT_ID = 130;
    private static final int DEVELOPMENT_ID = 51;
    private static final int ROOM_ID = 93;
    private static final int CLIENT_ID = 7;
    private static final int DEVELOPER_ID = 12;
    private static final int STRANGER_ID = 99;
    private static final int DELIVERABLE_ID = 401;
    private static final int OTHER_DELIVERABLE_ID = 402;
    private static final long FILE_ID = 3_000_000_001L;
    private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 9, 10, 6, 0);

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final DevelopmentContractRepository developmentContractRepository = mock(DevelopmentContractRepository.class);
    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private final DeliverableRepository deliverableRepository = mock(DeliverableRepository.class);
    private final DeliverableMaterialRepository materialRepository = mock(DeliverableMaterialRepository.class);
    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);
    private final FileLookupService fileLookupService = mock(FileLookupService.class);

    private final DeliverableMaterialService service = new DeliverableMaterialService(
            contractRepository, developmentContractRepository, milestoneRepository, deliverableRepository,
            materialRepository, roomQueryService, fileLookupService);

    private Contract contract;
    private final List<DeliverableMaterial> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        contract = contract(true);
        when(contractRepository.findWithLockByIdAndDeletedFalse(CONTRACT_ID)).thenAnswer(invocation -> Optional.of(contract));
        DevelopmentContract development = DevelopmentContract.of(
                CONTRACT_ID, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14);
        ReflectionTestUtils.setField(development, "id", DEVELOPMENT_ID);
        when(developmentContractRepository.findByIdAndDeletedFalse(DEVELOPMENT_ID)).thenReturn(Optional.of(development));
        when(developmentContractRepository.findByContractIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.of(development));
        when(roomQueryService.findParties(ROOM_ID)).thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_ID)));
        Deliverable deliverable = deliverable(DELIVERABLE_ID);
        when(deliverableRepository.findByIdAndDeletedFalse(DELIVERABLE_ID)).thenReturn(Optional.of(deliverable));
        when(deliverableRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(DEVELOPMENT_ID))
                .thenReturn(List.of(deliverable, deliverable(OTHER_DELIVERABLE_ID)));
        givenMilestones(milestone(false, "CONFIRMED"), milestone(true, "IN_PROGRESS"));
        when(materialRepository.findAllByDeliverableIdInAndDeletedFalseOrderByIdAsc(any())).thenAnswer(invocation -> {
            Collection<?> ids = invocation.getArgument(0);
            return stored.stream().filter(material -> ids.contains(material.getDeliverableId())).toList();
        });
        when(materialRepository.save(any())).thenAnswer(invocation -> {
            DeliverableMaterial material = invocation.getArgument(0);
            ReflectionTestUtils.setField(material, "id", 500 + stored.size());
            // 저장할 때 JPA 감사가 채우는 등록 시각. 자료마다 1분씩 늦게 둔다.
            ReflectionTestUtils.setField(material, "createdAt", REGISTERED_AT.plusMinutes(stored.size()));
            stored.add(material);
            return material;
        });
        when(deliverableRepository.save(any())).thenAnswer(invocation -> {
            Deliverable added = invocation.getArgument(0);
            ReflectionTestUtils.setField(added, "id", 410);
            return added;
        });
    }

    @Test
    @DisplayName("일반 작업을 모두 확정한 뒤 파일을 붙이면 자료가 하나 늘고 기존 URL은 남는다")
    void attachesFileKeepingOtherMaterials() {
        stored.add(DeliverableMaterial.ofUrl(DELIVERABLE_ID, "https://demo.example.com"));

        DeliverableResponse response = attach(FILE_ID, DeliverableAccessStage.PAID);

        assertThat(response.materials()).extracting(material -> material.fileId(), material -> material.url())
                .containsExactly(
                        tuple(null, "https://demo.example.com"),
                        tuple(FILE_ID, null));
        verify(fileLookupService).assertAttachableLong(DEVELOPER_ID, List.of(FILE_ID));
        verify(contractRepository).findWithLockByIdAndDeletedFalse(CONTRACT_ID);
    }

    @Test
    @DisplayName("자료를 등록하면 인계물의 넘긴 시각은 가장 최근에 등록한 자료의 시각이다. 다시 보낸 같은 자료는 시각을 바꾸지 않는다")
    void deliveredAtFollowsTheLatestMaterial() {
        assertThat(attach(FILE_ID, DeliverableAccessStage.TEST).deliveredAt())
                .isEqualTo(REGISTERED_AT.atOffset(ZoneOffset.UTC));
        assertThat(addUrl("https://demo.example.com").deliveredAt())
                .isEqualTo(REGISTERED_AT.plusMinutes(1).atOffset(ZoneOffset.UTC));
        assertThat(attach(FILE_ID, DeliverableAccessStage.TEST).deliveredAt())
                .isEqualTo(REGISTERED_AT.plusMinutes(1).atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("같은 파일을 같은 단계로 다시 보내면 새로 만들지 않는다")
    void sameFileSameStageIsIdempotent() {
        attach(FILE_ID, DeliverableAccessStage.TEST);
        DeliverableResponse again = attach(FILE_ID, DeliverableAccessStage.TEST);

        assertThat(again.materials()).hasSize(1);
        verify(materialRepository).save(any());
    }

    @Test
    @DisplayName("같은 계약에서 같은 파일을 다른 단계로 올리면 다른 산출물이어도 거부한다")
    void sameFileOtherStageConflictsAcrossTheContract() {
        stored.add(DeliverableMaterial.ofFile(OTHER_DELIVERABLE_ID, FILE_ID, DeliverableAccessStage.TEST));

        assertCode(() -> attach(FILE_ID, DeliverableAccessStage.PAID), ContractErrorCode.MATERIAL_STAGE_CONFLICT);
        verify(materialRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 파일을 같은 단계로 다른 산출물에 올리는 것은 막지 않는다")
    void sameFileSameStageOnOtherDeliverableIsAllowed() {
        stored.add(DeliverableMaterial.ofFile(OTHER_DELIVERABLE_ID, FILE_ID, DeliverableAccessStage.TEST));

        assertThat(attach(FILE_ID, DeliverableAccessStage.TEST).materials()).hasSize(1);
    }

    @Test
    @DisplayName("붙일 수 없는 파일이면 저장하지 않는다")
    void unattachableFileIsRejected() {
        doThrow(new BusinessException(FileErrorCode.FILE_NOT_ATTACHABLE))
                .when(fileLookupService).assertAttachableLong(DEVELOPER_ID, List.of(FILE_ID));

        assertCode(() -> attach(FILE_ID, DeliverableAccessStage.TEST), FileErrorCode.FILE_NOT_ATTACHABLE);
        verify(materialRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 인계물이거나 당사자가 아니면 없다고 답하고, 의뢰인은 권한이 없다")
    void strangersAreNotFoundAndClientIsForbidden() {
        assertCode(() -> service.attachFile(STRANGER_ID, DELIVERABLE_ID, request(FILE_ID, DeliverableAccessStage.TEST)),
                ContractErrorCode.DELIVERABLE_NOT_FOUND);
        assertCode(() -> service.attachFile(DEVELOPER_ID, 999, request(FILE_ID, DeliverableAccessStage.TEST)),
                ContractErrorCode.DELIVERABLE_NOT_FOUND);
        assertCode(() -> service.attachFile(CLIENT_ID, DELIVERABLE_ID, request(FILE_ID, DeliverableAccessStage.TEST)),
                CommonErrorCode.FORBIDDEN);
        verify(fileLookupService, never()).assertAttachableLong(any(), any());
    }

    @Test
    @DisplayName("취소된 계약이면 거부한다")
    void cancelledContractIsRejected() {
        contract = contract(false);
        contract.cancel();

        assertCode(() -> attach(FILE_ID, DeliverableAccessStage.TEST), ContractErrorCode.CONTRACT_CANCELLED);
    }

    @Test
    @DisplayName("양쪽이 서명하기 전이면 아직 열리지 않았다")
    void unsignedContractIsNotOpen() {
        contract = contract(false);

        assertCode(() -> attach(FILE_ID, DeliverableAccessStage.TEST), ContractErrorCode.HANDOVER_NOT_OPEN);
    }

    @Test
    @DisplayName("일반 작업이 하나라도 확정 전이면 아직 열리지 않았다")
    void unfinishedRegularWorkIsNotOpen() {
        givenMilestones(milestone(false, "CONFIRMED"), milestone(false, "SUBMITTED"), milestone(true, "IN_PROGRESS"));

        assertCode(() -> attach(FILE_ID, DeliverableAccessStage.TEST), ContractErrorCode.HANDOVER_NOT_OPEN);
        assertCode(() -> addUrl("https://demo.example.com"), ContractErrorCode.HANDOVER_NOT_OPEN);
    }

    @Test
    @DisplayName("인계 작업을 확정했으면 자료를 바꿀 수 없다")
    void confirmedHandoverClosesMaterials() {
        givenMilestones(milestone(false, "CONFIRMED"), milestone(true, "CONFIRMED"));

        assertCode(() -> attach(FILE_ID, DeliverableAccessStage.TEST), ContractErrorCode.FINAL_ACCEPTANCE_PASSED);
        assertCode(() -> addUrl("https://demo.example.com"), ContractErrorCode.FINAL_ACCEPTANCE_PASSED);
        assertCode(() -> service.addDeliverable(DEVELOPER_ID, CONTRACT_ID, new AddDeliverableRequest("추가")),
                ContractErrorCode.FINAL_ACCEPTANCE_PASSED);
    }

    @Test
    @DisplayName("인계 작업의 확인을 요청한 뒤에는 자료와 산출물 항목을 바꿀 수 없다")
    void submittedHandoverFreezesMaterials() {
        givenMilestones(milestone(false, "CONFIRMED"), milestone(true, "SUBMITTED"));

        assertCode(() -> attach(FILE_ID, DeliverableAccessStage.TEST), ContractErrorCode.HANDOVER_UNDER_REVIEW);
        assertCode(() -> addUrl("https://demo.example.com"), ContractErrorCode.HANDOVER_UNDER_REVIEW);
        assertCode(() -> service.addDeliverable(DEVELOPER_ID, CONTRACT_ID, new AddDeliverableRequest("추가")),
                ContractErrorCode.HANDOVER_UNDER_REVIEW);
        verify(materialRepository, never()).save(any());
        verify(deliverableRepository, never()).save(any());
    }

    @Test
    @DisplayName("최종 인수 시험은 계약 전체에 시험 자료(URL 또는 TEST)와 PAID 파일이 하나 이상씩 있어야 요청한다")
    void finalAcceptanceNeedsTestMaterialAndPaidFile() {
        DeliverableMaterial url = DeliverableMaterial.ofUrl(DELIVERABLE_ID, "https://demo.example.com");
        DeliverableMaterial test = DeliverableMaterial.ofFile(DELIVERABLE_ID, FILE_ID, DeliverableAccessStage.TEST);
        DeliverableMaterial paid = DeliverableMaterial.ofFile(DELIVERABLE_ID, FILE_ID + 1, DeliverableAccessStage.PAID);

        assertThat(readyForFinalAcceptance()).isFalse();
        assertThat(readyForFinalAcceptance(url)).isFalse();
        assertThat(readyForFinalAcceptance(test)).isFalse();
        assertThat(readyForFinalAcceptance(paid)).isFalse();
        assertThat(readyForFinalAcceptance(url, test)).isFalse();
        assertThat(readyForFinalAcceptance(url, paid)).isTrue();
        assertThat(readyForFinalAcceptance(test, paid)).isTrue();
        assertThat(readyForFinalAcceptance(url, test, paid)).isTrue();
    }

    @Test
    @DisplayName("시험 자료와 PAID 파일이 서로 다른 산출물에 있어도 된다")
    void finalAcceptanceMaterialsMaySpreadOverDeliverables() {
        assertThat(readyForFinalAcceptance(
                DeliverableMaterial.ofUrl(DELIVERABLE_ID, "https://demo.example.com"),
                DeliverableMaterial.ofFile(OTHER_DELIVERABLE_ID, FILE_ID, DeliverableAccessStage.PAID))).isTrue();
    }

    @Test
    @DisplayName("URL은 TEST로 더하고, 같은 URL을 다시 보내면 새로 만들지 않는다")
    void addsUrlOnceAsTest() {
        addUrl("https://demo.example.com");
        DeliverableResponse response = addUrl("https://demo.example.com");

        assertThat(response.materials()).extracting(material -> material.accessStage()).containsExactly("TEST");
        verify(materialRepository).save(any());
    }

    @Test
    @DisplayName("계약 뒤 더한 산출물은 계약서 밖 항목으로 표시된다")
    void addedDeliverableIsOutsideTheContract() {
        DeliverableResponse response =
                service.addDeliverable(DEVELOPER_ID, CONTRACT_ID, new AddDeliverableRequest("운영 설명서 보충"));

        ArgumentCaptor<Deliverable> saved = ArgumentCaptor.forClass(Deliverable.class);
        verify(deliverableRepository).save(saved.capture());
        assertThat(saved.getValue().isContractual()).isFalse();
        assertThat(saved.getValue().getDevelopmentContractId()).isEqualTo(DEVELOPMENT_ID);
        assertThat(response.isContractual()).isFalse();
        assertThat(response.title()).isEqualTo("운영 설명서 보충");
    }

    @Test
    @DisplayName("개발 계약이 아니거나 의뢰인이면 산출물을 더하지 못한다")
    void addDeliverableNeedsDeveloperOfDevelopmentContract() {
        assertCode(() -> service.addDeliverable(CLIENT_ID, CONTRACT_ID, new AddDeliverableRequest("추가")),
                CommonErrorCode.FORBIDDEN);
        when(developmentContractRepository.findByContractIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.empty());
        assertCode(() -> service.addDeliverable(DEVELOPER_ID, CONTRACT_ID, new AddDeliverableRequest("추가")),
                ContractErrorCode.CONTRACT_NOT_FOUND);
        verify(deliverableRepository, never()).save(any());
    }

    private DeliverableResponse attach(long fileId, DeliverableAccessStage stage) {
        return service.attachFile(DEVELOPER_ID, DELIVERABLE_ID, request(fileId, stage));
    }

    private DeliverableResponse addUrl(String url) {
        return service.addUrl(DEVELOPER_ID, DELIVERABLE_ID, new AddDeliverableUrlRequest(url));
    }

    private static AttachDeliverableFileRequest request(long fileId, DeliverableAccessStage stage) {
        return new AttachDeliverableFileRequest(fileId, stage);
    }

    private boolean readyForFinalAcceptance(DeliverableMaterial... materials) {
        stored.clear();
        stored.addAll(List.of(materials));
        DevelopmentContract development = developmentContractRepository.findByIdAndDeletedFalse(DEVELOPMENT_ID).orElseThrow();
        try {
            service.requireFinalAcceptanceMaterials(development);
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ContractErrorCode.HANDOVER_MATERIALS_MISSING);
            return false;
        }
    }

    private void givenMilestones(Milestone... milestones) {
        when(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(DEVELOPMENT_ID))
                .thenReturn(List.of(milestones));
    }

    private static Milestone milestone(boolean handover, String status) {
        Milestone milestone = Milestone.of(DEVELOPMENT_ID, 312, null, null, handover ? 0 : 100000, null, handover);
        ReflectionTestUtils.setField(milestone, "status", status);
        return milestone;
    }

    private static Deliverable deliverable(int id) {
        Deliverable deliverable = Deliverable.of(DEVELOPMENT_ID, "소스 파일 전체");
        ReflectionTestUtils.setField(deliverable, "id", id);
        return deliverable;
    }

    private static Contract contract(boolean concluded) {
        Contract contract = Contract.of(ROOM_ID, Contract.ContractType.DEVELOPMENT);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        if (concluded) {
            ReflectionTestUtils.setField(contract, "status", "SIGNED");
        }
        return contract;
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
