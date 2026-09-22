package com.a501.service.domain.developer.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CareerTest {

    @Test
    void verifiedCareerKeepsTheFactsThatWereVerified() {
        Career career = Career.create(3, "기존 회사", "백엔드",
                LocalDate.of(2022, 1, 1), LocalDate.of(2024, 1, 1), 512);
        ReflectionTestUtils.setField(career, "verified", true);

        career.update("바꾼 회사", "대표",
                LocalDate.of(2000, 1, 1), null, 999);

        assertThat(career.getCompany()).isEqualTo("기존 회사");
        assertThat(career.getRole()).isEqualTo("백엔드");
        assertThat(career.getStartDate()).isEqualTo(LocalDate.of(2022, 1, 1));
        assertThat(career.getEndDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(career.getFileId()).isEqualTo(512);
        assertThat(career.isVerified()).isTrue();
    }
}
