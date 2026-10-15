package com.a501.service.domain.defaults.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.defaults.DefaultErrorCode;
import com.a501.service.domain.defaults.dto.request.DefaultSort;
import com.a501.service.domain.defaults.dto.response.DefaultListResponse;
import com.a501.service.domain.defaults.dto.response.DefaultResponse;
import com.a501.service.domain.defaults.entity.Default;
import com.a501.service.domain.defaults.repository.DefaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/** 서비스 계층의 규칙(404 조건, 1-based → 0-based 페이지 변환, 정렬 전달)을 DB 없이 검증합니다. */
@ExtendWith(MockitoExtension.class)
class DefaultServiceTest {
    @Mock
    private DefaultRepository defaultRepository;

    private DefaultService defaultService;

    @BeforeEach
    void setUp() {
        defaultService = new DefaultService(defaultRepository);
    }

    @Test
    @DisplayName("살아 있는 레코드는 id와 message만 담아 돌려준다")
    void getDefaultMapsEntity() {
        Default entity = Default.of("default");
        ReflectionTestUtils.setField(entity, "id", 1);
        when(defaultRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(entity));

        assertThat(defaultService.getDefault(1)).isEqualTo(new DefaultResponse(1, "default"));
    }

    @Test
    @DisplayName("없거나 삭제된 레코드는 DEFAULT_NOT_FOUND를 던진다")
    void getDefaultThrowsWhenMissing() {
        when(defaultRepository.findByIdAndDeletedFalse(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> defaultService.getDefault(2))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DefaultErrorCode.DEFAULT_NOT_FOUND));
    }

    @Test
    @DisplayName("계약의 1-based page를 Spring Data의 0-based로 옮기고 응답에는 요청한 번호를 되돌린다")
    void getDefaultsTranslatesPageNumber() {
        Pageable expected = PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "id"));
        when(defaultRepository.findActivePage(expected)).thenReturn(new PageImpl<>(
                List.of(new DefaultResponse(30, "default")), expected, 137));

        DefaultListResponse response = defaultService.getDefaults(3, 10, DefaultSort.ID_DESC);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(defaultRepository).findActivePage(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "id"));
        assertThat(response).isEqualTo(new DefaultListResponse(
                List.of(new DefaultResponse(30, "default")), 3, 10, 137, 14));
    }

    @Test
    @DisplayName("마지막 페이지를 넘어선 요청은 빈 content와 채워진 페이지 정보다")
    void getDefaultsBeyondLastPageIsEmptyNotError() {
        Pageable pageable = PageRequest.of(98, 20, Sort.by(Sort.Direction.ASC, "id"));
        when(defaultRepository.findActivePage(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 137));

        DefaultListResponse response = defaultService.getDefaults(99, 20, DefaultSort.ID_ASC);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(99);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(137);
        assertThat(response.totalPages()).isEqualTo(7);
    }
}
