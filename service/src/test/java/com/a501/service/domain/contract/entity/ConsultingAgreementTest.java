package com.a501.service.domain.contract.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

class ConsultingAgreementTest {

    @Test
    void topicColumnAcceptsTheSameLengthAsTheRequest() throws NoSuchFieldException {
        Column column = ConsultingAgreement.class
                .getDeclaredField("topic")
                .getAnnotation(Column.class);

        assertThat(column.length()).isEqualTo(255);
    }
}
