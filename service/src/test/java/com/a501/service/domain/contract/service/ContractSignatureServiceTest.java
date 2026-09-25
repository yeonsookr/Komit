package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.SignContractRequest;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.ContractSignature;
import com.a501.service.domain.contract.entity.ContractTemplate;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.ContractSignatureRepository;
import com.a501.service.domain.contract.repository.ContractTemplateRepository;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 계약서 서명 규칙을 DB 없이 검증합니다.
 *
 * <p>양쪽이 모두 서명해야 계약이 성립하고 (`R-CON-01`), 같은 사람이 두 번 서명할 수는 없습니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractSignatureServiceTest {
    private static final Integer ROOM_ID = 92;
    private static final Integer CONTRACT_ID = 120;
    private static final Integer TEMPLATE_ID = 3;
    private static final Integer CLIENT_ID = 9001;
    private static final Integer DEVELOPER_USER_ID = 9101;
    private static final Integer STRANGER_ID = 9999;
    private static final Integer FILE_ID = 41;
    private static final Instant NOW = Instant.parse("2026-09-25T02:00:00Z");

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractSignatureRepository signatureRepository;
    @Mock
    private ContractTemplateRepository templateRepository;
    @Mock
    private RoomQueryService roomQueryService;
    @Mock
    private ContractDetailService contractDetailService;

    private ContractSignatureService service;
    private Contract contract;

    @BeforeEach
    void setUp() {
        service = new ContractSignatureService(
                contractRepository,
                signatureRepository,
                templateRepository,
                roomQueryService,
                contractDetailService,
                Clock.fixed(NOW, ZoneOffset.UTC));

        contract = Contract.of(ROOM_ID, Contract.ContractType.CONSULTING);
        ReflectionTestUtils.setField(contract, "id", CONTRACT_ID);
        ReflectionTestUtils.setField(contract, "templateId", TEMPLATE_ID);

        when(contractRepository.findWithLockByIdAndDeletedFalse(CONTRACT_ID))
                .thenReturn(Optional.of(contract));
        when(roomQueryService.findParties(ROOM_ID))
                .thenReturn(Optional.of(new RoomParties(CLIENT_ID, DEVELOPER_USER_ID)));

        AtomicInteger signatureId = new AtomicInteger(500);
        when(signatureRepository.save(any(ContractSignature.class))).thenAnswer(call -> {
            ContractSignature saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", signatureId.incrementAndGet());
            return saved;
        });

        ContractTemplate template = org.mockito.Mockito.mock(ContractTemplate.class);
        when(template.getBody()).thenReturn("제1조 …");
        when(templateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
    }

    @Test
    @DisplayName("의뢰인이 취소한 계약에는 개발자가 뒤늦게 서명해도 받지 않는다 (R-CON-21)")
    void rejectsSignatureOnCancelledContract() {
        contract.cancel();

        assertThatThrownBy(() -> service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(FILE_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_CANCELLED);
        assertThat(contract.getDeveloperSignatureId()).isNull();
        assertThat(contract.isConcluded()).isFalse();
    }

    @Test
    @DisplayName("한쪽만 서명하면 협의중으로 남는다")
    void staysNegotiatingUntilBothSign() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));

        assertThat(contract.getClientSignatureId()).isNotNull();
        assertThat(contract.getDeveloperSignatureId()).isNull();
        assertThat(contract.getStatus()).isEqualTo("NEGOTIATING");
        assertThat(contract.isConcluded()).isFalse();
        // 아직 합의하지 않았으므로 문구를 굳히지 않는다.
        assertThat(contract.getContractText()).isNull();
    }

    @Test
    @DisplayName("양쪽이 서명하면 계약이 성립한다")
    void concludesWhenBothSign() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));
        service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(42));

        assertThat(contract.isConcluded()).isTrue();
        assertThat(contract.getStatus()).isEqualTo("SIGNED");
    }

    @Test
    @DisplayName("성립한 순간의 계약 문구를 그대로 굳힌다")
    void freezesContractText() {
        service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(FILE_ID));
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(42));

        // 나중에 양식이 바뀌어도 이 계약은 그때 합의한 글이어야 한다.
        assertThat(contract.getContractText()).isEqualTo("제1조 …");
    }

    @Test
    @DisplayName("양식이 없으면 문구를 비워 두고 서명은 그대로 받는다")
    void signsEvenWithoutTemplate() {
        ReflectionTestUtils.setField(contract, "templateId", null);

        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));
        service.sign(CONTRACT_ID, DEVELOPER_USER_ID, new SignContractRequest(42));

        assertThat(contract.isConcluded()).isTrue();
        assertThat(contract.getContractText()).isNull();
    }

    @Test
    @DisplayName("서명 시각과 그림을 남긴다")
    void recordsSignature() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));

        org.mockito.ArgumentCaptor<ContractSignature> captor =
                org.mockito.ArgumentCaptor.forClass(ContractSignature.class);
        verify(signatureRepository).save(captor.capture());
        ContractSignature saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(CLIENT_ID);
        assertThat(saved.getSignatureImageFileId()).isEqualTo(FILE_ID);
        assertThat(saved.isSigned()).isTrue();
        assertThat(saved.getSignedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("같은 사람이 두 번 서명할 수는 없다")
    void rejectsSecondSignatureFromSamePerson() {
        service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(FILE_ID));

        assertThatThrownBy(
                        () -> service.sign(CONTRACT_ID, CLIENT_ID, new SignContractRequest(42)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.ALREADY_SIGNED_BY_ME);
    }

    @Test
    @DisplayName("당사자가 아니면 계약이 있다는 것도 알리지 않는다")
    void hidesContractFromStranger() {
        assertThatThrownBy(
                        () -> service.sign(CONTRACT_ID, STRANGER_ID, new SignContractRequest(FILE_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_FOUND);
        verify(signatureRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 계약은 404 다")
    void rejectsMissingContract() {
        when(contractRepository.findWithLockByIdAndDeletedFalse(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sign(404, CLIENT_ID, new SignContractRequest(FILE_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.CONTRACT_NOT_FOUND);
    }
}
