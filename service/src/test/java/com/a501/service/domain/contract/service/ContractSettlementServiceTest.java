package com.a501.service.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.repository.ConsultingAgreementRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 지급이 끝난 개발 계약에 완료를 적는다. 진행 중인 계약만 완료가 된다. (R-PAY-07) */
class ContractSettlementServiceTest {
    private static final int DEVELOPMENT_ID = 51;

    private final DevelopmentContractRepository developmentContractRepository = mock(DevelopmentContractRepository.class);
    private final ContractSettlementService service =
            new ContractSettlementService(mock(ConsultingAgreementRepository.class), developmentContractRepository);

    @Test
    @DisplayName("진행 중인 개발 계약을 완료로 적는다")
    void completesInProgressDevelopment() {
        DevelopmentContract development = development();
        development.start();
        when(developmentContractRepository.findByIdAndDeletedFalse(DEVELOPMENT_ID)).thenReturn(Optional.of(development));

        service.completeDevelopmentPayout(DEVELOPMENT_ID);

        assertThat(development.isCompleted()).isTrue();
        assertThat(development.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("착수 전이거나 이미 완료된 계약은 그대로 둔다. 없으면 아무 일도 하지 않는다")
    void leavesOtherStatesAlone() {
        DevelopmentContract beforeStart = development();
        when(developmentContractRepository.findByIdAndDeletedFalse(DEVELOPMENT_ID)).thenReturn(Optional.of(beforeStart));
        service.completeDevelopmentPayout(DEVELOPMENT_ID);
        assertThat(beforeStart.getStatus()).isEqualTo("SIGNED");

        when(developmentContractRepository.findByIdAndDeletedFalse(DEVELOPMENT_ID)).thenReturn(Optional.empty());
        service.completeDevelopmentPayout(DEVELOPMENT_ID);
    }

    @Test
    @DisplayName("진행 중이 아닌 개발 계약은 엔티티도 완료를 거부한다")
    void entityRefusesToCompleteOutsideProgress() {
        assertThatThrownBy(development()::complete).isInstanceOf(IllegalStateException.class);
    }

    private static DevelopmentContract development() {
        return DevelopmentContract.of(130, LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14);
    }
}
