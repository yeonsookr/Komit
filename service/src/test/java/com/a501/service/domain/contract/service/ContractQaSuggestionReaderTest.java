package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.contract.ContractErrorCode;
import com.a501.service.domain.contract.dto.request.ContractQaSuggestionRequest;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.diagnosis.service.ContractRequirementService;
import com.a501.service.domain.room.dto.ContractRoomView;
import com.a501.service.domain.room.dto.RoomParties;
import com.a501.service.domain.room.service.RoomQueryService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ContractQaSuggestionReaderTest {
    private static final int ROOM_ID = 93;
    private static final int PROJECT_ID = 8;
    private static final int USER_ID = 12;

    private final RoomQueryService roomQueryService = mock(RoomQueryService.class);
    private final ContractRequirementService requirementService = mock(ContractRequirementService.class);
    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final DevelopmentContractRepository developmentRepository = mock(DevelopmentContractRepository.class);
    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private ContractQaSuggestionReader reader;

    @BeforeEach
    void setUp() {
        DevelopmentContractEditingPolicy policy =
                new DevelopmentContractEditingPolicy(roomQueryService, requirementService);
        reader = new ContractQaSuggestionReader(
                policy, contractRepository, developmentRepository, milestoneRepository);
        when(roomQueryService.findContractRoom(ROOM_ID)).thenReturn(Optional.of(
                new ContractRoomView(
                        ROOM_ID, "DEVELOPMENT", PROJECT_ID, new RoomParties(11, USER_ID))));
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.empty());
        when(requirementService.findRequestIssueIds(PROJECT_ID)).thenReturn(Set.of(312));
    }

    @Test
    void usesCurrentEditedTextForExistingAndUnsavedWork() {
        assertThat(reader.read(
                        ROOM_ID,
                        USER_ID,
                        new ContractQaSuggestionRequest(312, "바꾼 이름", "바꾼 설명")))
                .isEqualTo("바꾼 이름" + System.lineSeparator() + "바꾼 설명");
        assertThat(reader.read(
                        ROOM_ID,
                        USER_ID,
                        new ContractQaSuggestionRequest(null, "새 작업", null)))
                .isEqualTo("새 작업");
    }

    @Test
    void acceptsIssueAlreadyInCurrentContract() {
        Contract contract = contract();
        DevelopmentContract development = DevelopmentContract.of(
                130, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14);
        ReflectionTestUtils.setField(development, "id", 51);
        Milestone milestone = Milestone.of(51, 320, null, null, 1, null);
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.of(contract));
        when(developmentRepository.findByContractIdAndDeletedFalse(130))
                .thenReturn(Optional.of(development));
        when(milestoneRepository.findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(51))
                .thenReturn(List.of(milestone));

        assertThat(reader.read(
                        ROOM_ID,
                        USER_ID,
                        new ContractQaSuggestionRequest(320, "기존 계약 작업", null)))
                .isEqualTo("기존 계약 작업");
    }

    @Test
    void rejectsUnknownIssueAndNonEditableContract() {
        assertCode(
                () -> reader.read(
                        ROOM_ID,
                        USER_ID,
                        new ContractQaSuggestionRequest(999, "남의 작업", null)),
                CommonErrorCode.INVALID_REQUEST);

        Contract signed = contract();
        signed.sign(true, 77, null);
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.of(signed));
        assertCode(
                () -> reader.read(
                        ROOM_ID,
                        USER_ID,
                        new ContractQaSuggestionRequest(null, "새 작업", null)),
                ContractErrorCode.CONTRACT_ALREADY_SIGNED);

        Contract cancelled = contract();
        cancelled.cancel();
        when(contractRepository.findByRoomIdAndDeletedFalse(ROOM_ID)).thenReturn(Optional.of(cancelled));
        assertCode(
                () -> reader.read(
                        ROOM_ID,
                        USER_ID,
                        new ContractQaSuggestionRequest(null, "새 작업", null)),
                ContractErrorCode.CONTRACT_CANCELLED);
    }

    private Contract contract() {
        Contract contract = Contract.of(ROOM_ID, Contract.ContractType.DEVELOPMENT);
        ReflectionTestUtils.setField(contract, "id", 130);
        return contract;
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Object errorCode) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorCode()).isEqualTo(errorCode));
    }
}
