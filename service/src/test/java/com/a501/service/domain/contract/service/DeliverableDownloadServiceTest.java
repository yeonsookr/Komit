package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.ErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DeliverableAccessStage;
import com.a501.service.domain.contract.entity.DeliverableMaterial;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DeliverableMaterialRepository;
import com.a501.service.domain.contract.repository.DeliverableRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.file.service.FileLookupService.FileContent;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 인계 파일 권한을 파일마다 따로 본다. TEST 는 양쪽이, PAID 는 개발자와 지급 뒤 의뢰인이 받는다. (R-QA-06) */
class DeliverableDownloadServiceTest {
    private static final int CONTRACT_ID = 130;
    private static final int DEVELOPMENT_ID = 51;
    private static final int ROOM_ID = 93;
    private static final int CLIENT_ID = 7;
    private static final int DEVELOPER_ID = 12;
    private static final int STRANGER_ID = 99;
    private static final int DELIVERABLE_ID = 401;
    private static final int TEST_ID = 501;
    private static final int PAID_ID = 502;
    private static final int URL_ID = 503;
    private static final long TEST_FILE = 3_000_000_001L;
    private static final long PAID_FILE = 3_000_000_002L;

    private final DeliverableMaterialRepository materialRepository = mock(DeliverableMaterialRepository.class);
    private final DeliverableRepository deliverableRepository = mock(DeliverableRepository.class);
    private final DevelopmentContractRepository developmentContractRepository = mock(DevelopmentContractRepository.class);
    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);
    private final FileLookupService fileLookupService = mock(FileLookupService.class);

    private final DeliverableDownloadService service = new DeliverableDownloadService(
            materialRepository, deliverableRepository, developmentContractRepository, contractRepository,
            roomQueryService, fileLookupService);

    private DevelopmentContract development;
    private Deliverable deliverable;

    @BeforeEach
    void setUp() {
        deliverable = Deliverable.of(DEVELOPMENT_ID, "소스와 실행 주소");
        ReflectionTestUtils.setField(deliverable, "id", DELIVERABLE_ID);
        when(deliverableRepository.findByIdAndDeletedFalse(DELIVERABLE_ID)).thenReturn(Optional.of(deliverable));
        development = DevelopmentContract.of(CONTRACT_ID, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14);
        ReflectionTestUtils.setField(development, "id", DEVELOPMENT_ID);
        development.start();
        when(developmentContractRepository.findByIdAndDeletedFalse(DEVELOPMENT_ID)).thenReturn(Optional.of(development));
        Contract contract = Contract.of(ROOM_ID, Contract.ContractType.DEVELOPMENT);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        when(contractRepository.findByIdAndDeletedFalse(CONTRACT_ID)).thenReturn(Optional.of(contract));
        when(roomQueryService.findParties(ROOM_ID)).thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_ID)));

        material(TEST_ID, DeliverableMaterial.ofFile(DELIVERABLE_ID, TEST_FILE, DeliverableAccessStage.TEST));
        material(PAID_ID, DeliverableMaterial.ofFile(DELIVERABLE_ID, PAID_FILE, DeliverableAccessStage.PAID));
        material(URL_ID, DeliverableMaterial.ofUrl(DELIVERABLE_ID, "https://demo.example.com"));
        when(fileLookupService.open(any())).thenAnswer(invocation -> Optional.of(
                new FileContent("file-" + invocation.getArgument(0), 3L, new ByteArrayInputStream(new byte[3]))));
    }

    @Test
    @DisplayName("TEST 파일은 지급 전에도 양쪽 당사자가 받는다")
    void testFileIsOpenToBothParties() {
        assertThat(service.downloadMaterial(CLIENT_ID, TEST_ID).originalName()).isEqualTo("file-" + TEST_FILE);
        assertThat(service.downloadMaterial(DEVELOPER_ID, TEST_ID).originalName()).isEqualTo("file-" + TEST_FILE);
    }

    @Test
    @DisplayName("PAID 파일은 지급 전 의뢰인에게 주지 않고, 같은 산출물에 TEST·URL 이 있어도 열리지 않는다")
    void paidFileIsLockedForClientBeforePayout() {
        assertCode(() -> service.downloadMaterial(CLIENT_ID, PAID_ID), ContractErrorCode.DELIVERABLE_ACCESS_LOCKED);

        verify(fileLookupService, never()).open(PAID_FILE);
    }

    @Test
    @DisplayName("올린 개발자는 지급 전에도 자기 PAID 파일을 받는다")
    void developerOpensOwnPaidFile() {
        assertThat(service.downloadMaterial(DEVELOPER_ID, PAID_ID).originalName()).isEqualTo("file-" + PAID_FILE);
    }

    @Test
    @DisplayName("지급이 끝나 개발 계약이 완료되면 의뢰인도 PAID 파일을 받는다")
    void clientOpensPaidFileAfterPayout() {
        development.complete();

        assertThat(service.downloadMaterial(CLIENT_ID, PAID_ID).originalName()).isEqualTo("file-" + PAID_FILE);
    }

    @Test
    @DisplayName("URL 자료는 받을 파일이 없다")
    void urlMaterialHasNoFile() {
        assertCode(() -> service.downloadMaterial(CLIENT_ID, URL_ID), ContractErrorCode.DELIVERABLE_NOT_FOUND);
    }

    @Test
    @DisplayName("당사자가 아니거나 없는 자료면 존재를 알리지 않는다")
    void strangersAndMissingAreNotFound() {
        assertCode(() -> service.downloadMaterial(STRANGER_ID, TEST_ID), ContractErrorCode.DELIVERABLE_NOT_FOUND);
        when(materialRepository.findByIdAndDeletedFalse(404)).thenReturn(Optional.empty());
        assertCode(() -> service.downloadMaterial(CLIENT_ID, 404), ContractErrorCode.DELIVERABLE_NOT_FOUND);
        when(fileLookupService.open(TEST_FILE)).thenReturn(Optional.empty());
        assertCode(() -> service.downloadMaterial(CLIENT_ID, TEST_ID), ContractErrorCode.DELIVERABLE_NOT_FOUND);

        verify(fileLookupService, never()).open(PAID_FILE);
    }

    @Test
    @DisplayName("다른 계약의 자료는 그 계약의 당사자에게만 보인다")
    void materialOfAnotherContractIsHidden() {
        Deliverable foreign = Deliverable.of(52, "다른 계약");
        ReflectionTestUtils.setField(foreign, "id", 402);
        when(deliverableRepository.findByIdAndDeletedFalse(402)).thenReturn(Optional.of(foreign));
        DevelopmentContract other = DevelopmentContract.of(140, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14);
        ReflectionTestUtils.setField(other, "id", 52);
        when(developmentContractRepository.findByIdAndDeletedFalse(52)).thenReturn(Optional.of(other));
        Contract otherContract = Contract.of(94, Contract.ContractType.DEVELOPMENT);
        ReflectionTestUtils.setField(otherContract, "id", 140);
        when(contractRepository.findByIdAndDeletedFalse(140)).thenReturn(Optional.of(otherContract));
        when(roomQueryService.findParties(94)).thenReturn(Optional.of(new RoomParties(31, 32)));
        material(509, DeliverableMaterial.ofFile(402, 71L, DeliverableAccessStage.TEST));

        assertCode(() -> service.downloadMaterial(CLIENT_ID, 509), ContractErrorCode.DELIVERABLE_NOT_FOUND);
    }

    @Test
    @DisplayName("기존 단일 파일은 PAID 로 다룬다. 없으면 아직 올라오지 않았다고 답한다")
    void legacyFileIsPaid() {
        assertCode(() -> service.downloadLegacy(CLIENT_ID, DELIVERABLE_ID), ContractErrorCode.DELIVERABLE_NOT_UPLOADED);

        ReflectionTestUtils.setField(deliverable, "fileId", 88);
        assertCode(() -> service.downloadLegacy(CLIENT_ID, DELIVERABLE_ID), ContractErrorCode.DELIVERABLE_ACCESS_LOCKED);
        assertThat(service.downloadLegacy(DEVELOPER_ID, DELIVERABLE_ID).originalName()).isEqualTo("file-88");
        development.complete();
        assertThat(service.downloadLegacy(CLIENT_ID, DELIVERABLE_ID).originalName()).isEqualTo("file-88");
        assertCode(() -> service.downloadLegacy(STRANGER_ID, DELIVERABLE_ID), ContractErrorCode.DELIVERABLE_NOT_FOUND);
    }

    private void material(int id, DeliverableMaterial material) {
        ReflectionTestUtils.setField(material, "id", id);
        when(materialRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(material));
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
