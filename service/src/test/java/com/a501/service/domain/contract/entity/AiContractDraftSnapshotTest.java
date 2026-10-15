package com.a501.service.domain.contract.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.a501.service.domain.contract.dto.response.ContractResponse;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiContractDraftSnapshotTest {
    @Test
    @DisplayName("계약과 생성 당시 JSON만 받아 불변 스냅샷을 만든다")
    void createsImmutableSnapshot() {
        AiContractDraftSnapshot snapshot =
                AiContractDraftSnapshot.of(31, "{\"draft\":{\"topic\":\"인증 오류\",\"detail\":null}}");

        assertThat(snapshot.getContractId()).isEqualTo(31);
        assertThat(snapshot.getContentSnapshot()).contains("인증 오류");
    }

    @Test
    @DisplayName("계약이나 원본 JSON이 없으면 만들지 않는다")
    void requiresContractAndContent() {
        assertThatNullPointerException()
                .isThrownBy(() -> AiContractDraftSnapshot.of(null, "{}"));
        assertThatNullPointerException()
                .isThrownBy(() -> AiContractDraftSnapshot.of(31, null));
    }

    @Test
    @DisplayName("AI 원본은 일반 계약 조회 응답에 노출하지 않는다")
    void staysOutOfPublicContractResponse() {
        assertThat(Arrays.stream(ContractResponse.class.getRecordComponents())
                        .map(component -> component.getName()))
                .doesNotContain("contentSnapshot", "aiDraftSnapshot");
    }
}
