package com.a501.service.domain.contract.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.diagnosis.dto.IssueBrief;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MilestoneResponseTest {
    @Test
    void handoverIsReadFromTheMarkNotThePrice() {
        MilestoneResponse handover = MilestoneResponse.of(
                Milestone.of(51, 320, null, null, 0, null, true), new IssueBrief("인계", null, null), List.of());
        MilestoneResponse freeWork = MilestoneResponse.of(
                Milestone.of(51, 312, null, null, 0, null), new IssueBrief("무료 작업", null, null), List.of());

        assertThat(handover.isHandover()).isTrue();
        assertThat(freeWork.isHandover()).isFalse();
    }

    @Test
    void jsonNamesTheMarkIsHandoverLikeTheMock() {
        MilestoneResponse handover = MilestoneResponse.of(
                Milestone.of(51, 320, null, null, 0, null, true), new IssueBrief("인계", null, null), List.of());

        JsonNode json = JsonMapper.builder().build().valueToTree(handover);

        assertThat(json.get("isHandover").asBoolean()).isTrue();
        assertThat(json.has("handover")).isFalse();
    }
}
