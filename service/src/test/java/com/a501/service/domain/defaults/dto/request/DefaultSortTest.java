package com.a501.service.domain.defaults.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

/**
 * 계약 값과 enum 상수의 대응을 확인합니다. 허용되지 않은 값이 422로 바뀌는 문구는
 * {@code DefaultControllerTest}가 보안 체인과 함께 끝까지 검증합니다.
 */
class DefaultSortTest {

    @Test
    @DisplayName("계약 값으로 상수를 찾고 Spring Data 정렬로 바꾼다")
    void mapsContractValues() {
        assertThat(DefaultSort.fromValue("id,asc")).isEqualTo(DefaultSort.ID_ASC);
        assertThat(DefaultSort.fromValue("id,desc")).isEqualTo(DefaultSort.ID_DESC);
        assertThat(DefaultSort.ID_ASC.toSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
        assertThat(DefaultSort.ID_DESC.toSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Test
    @DisplayName("상수 이름이나 빈 값은 받지 않는다")
    void rejectsUnknownValues() {
        assertThatThrownBy(() -> DefaultSort.fromValue("ID_ASC")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DefaultSort.fromValue("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString은 계약 값이다. 전역 예외 처리기가 이 값을 나열해 목과 같은 허용 목록 문구를 만든다")
    void toStringIsContractValue() {
        assertThat(DefaultSort.ID_ASC).hasToString("id,asc");
        assertThat(DefaultSort.ID_DESC).hasToString("id,desc");
        assertThat(DefaultSort.ID_ASC.value()).isEqualTo("id,asc");
    }
}
