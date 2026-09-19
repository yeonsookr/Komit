package com.a501.service.domain.defaults.repository;

import com.a501.service.domain.defaults.dto.response.DefaultResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * QueryDSL로 구현하는 조회 선언. 구현은 {@link DefaultRepositoryImpl}이며 Spring Data가 이름 규칙({@code *Impl})으로 이어 붙입니다.
 */
public interface DefaultRepositoryCustom {

    /**
     * 소프트 삭제되지 않은 레코드를 페이지로 조회합니다. 엔티티가 아니라 응답 DTO에 필요한 컬럼만 담아 옵니다.
     *
     * @param pageable 0부터 시작하는 페이지 번호, 크기, 정렬. 정렬 속성은 {@code id}만 허용합니다.
     * @return 항목과 전체 건수. 마지막 페이지를 넘어선 요청이면 항목은 비어 있고 전체 건수는 채워집니다.
     * @throws org.springframework.dao.InvalidDataAccessApiUsageException 지원하지 않는 정렬 속성이 왔을 때. 구현은
     *         {@link IllegalArgumentException}을 던지고 Spring Data 프록시의 예외 변환이 이 타입으로 감쌉니다.
     *         요청 검증은 컨트롤러가 끝냈으므로 여기까지 오면 코드 오류이며, 전역 예외 처리기가 500으로 바꿉니다.
     */
    Page<DefaultResponse> findActivePage(Pageable pageable);
}
