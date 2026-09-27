package com.a501.service.domain.contract.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.domain.contract.entity.Deliverable;
import com.a501.service.domain.contract.entity.DeliverableAccessStage;
import com.a501.service.domain.contract.entity.DeliverableMaterial;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DeliverableResponseTest {
    @Test
    void urlsAndFilesOfBothStagesAppearInOneDeliverable() {
        DeliverableResponse result = DeliverableResponse.from(deliverable(), List.of(
                material(DeliverableMaterial.ofFile(401, 71L, DeliverableAccessStage.TEST), 501),
                material(DeliverableMaterial.ofFile(401, 72L, DeliverableAccessStage.PAID), 502),
                material(DeliverableMaterial.ofUrl(401, "https://demo.example.com"), 503)));

        assertThat(result.materials()).extracting(DeliverableMaterialResponse::fileId).containsExactly(71L, 72L, null);
        assertThat(result.materials()).extracting(DeliverableMaterialResponse::url)
                .containsExactly(null, null, "https://demo.example.com");
        assertThat(result.materials()).extracting(DeliverableMaterialResponse::accessStage)
                .containsExactly("TEST", "PAID", "TEST");
        assertThat(result.isContractual()).isTrue();
    }

    @Test
    void urlMaterialIsAlwaysReadDuringTheAcceptanceTest() {
        assertThat(DeliverableMaterial.ofUrl(401, "https://demo.example.com").getAccessStage())
                .isEqualTo(DeliverableAccessStage.TEST);
    }

    @Test
    void jsonUsesTheSameFieldNamesAsTheMock() {
        DeliverableResponse result = DeliverableResponse.from(deliverable(), List.of(
                material(DeliverableMaterial.ofUrl(401, "https://demo.example.com"), 503)));

        JsonNode json = JsonMapper.builder().build().valueToTree(result);

        assertThat(json.has("isContractual")).isTrue();
        assertThat(json.get("materials").get(0).get("deliverableMaterialId").asInt()).isEqualTo(503);
        assertThat(json.get("materials").get(0).get("fileId").isNull()).isTrue();
    }

    private static Deliverable deliverable() {
        Deliverable deliverable = Deliverable.of(51, "완성된 웹사이트");
        ReflectionTestUtils.setField(deliverable, "id", 401);
        return deliverable;
    }

    private static DeliverableMaterial material(DeliverableMaterial material, Integer id) {
        ReflectionTestUtils.setField(material, "id", id);
        ReflectionTestUtils.setField(material, "createdAt", LocalDateTime.of(2026, 9, 10, 6, 0));
        return material;
    }
}
