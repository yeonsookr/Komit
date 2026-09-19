package com.a501.service.domain.defaults.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.defaults.DefaultErrorCode;
import com.a501.service.domain.defaults.dto.request.DefaultSort;
import com.a501.service.domain.defaults.dto.response.DefaultListResponse;
import com.a501.service.domain.defaults.dto.response.DefaultResponse;
import com.a501.service.domain.defaults.repository.DefaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기준 API 유스케이스. 조회만 있으므로 클래스 전체가 읽기 전용 트랜잭션이며, 읽기 DB로 라우팅됩니다.
 * ({@code config/DataSourceRoutingConfig})
 *
 * <p><b>권한과 403에 대해.</b> 이 API는 인증된 사용자만 부를 수 있고(SecurityConfig의 {@code anyRequest().authenticated()}),
 * 예시 레코드에는 소유자가 없어 사용자별 접근 차이가 없습니다. 목 명세의 403 {@code FORBIDDEN}은 "리소스의 존재가 공개된 경우에만
 * 쓴다"는 규약을 보여 주기 위한 문서상의 사례이며, 이 도메인에서는 발생 조건이 없어 구현하지 않습니다.
 * 소유자가 있는 도메인에서는 다음 규칙으로 갈립니다. (BACKEND_CONVENTIONS 12.1)
 * <ul>
 *   <li>존재 자체가 민감한 리소스(요청서·계약·정산·채팅)에 타인이 접근 → 존재를 감추기 위해 404</li>
 *   <li>존재가 공개된 리소스(공개 프로필·공개 목록), 또는 당사자가 자기 역할에 맞지 않는 동작을 요청 → 403</li>
 * </ul>
 * 그 판단은 여기처럼 서비스 계층에서 인증 사용자 id와 리소스의 소유자를 대조해 내립니다. 컨트롤러가 넘긴 사용자 id는 신뢰하지 않습니다.
 *
 * <p><b>500에 대해.</b> 목은 {@code id}=4·{@code page}=99에서 500을 재현하지만 실서버에는 그런 분기가 없습니다.
 * 예상 밖 예외는 전역 예외 처리기가 {@code INTERNAL_ERROR} 고정 문구로 바꿉니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultService {
    private final DefaultRepository defaultRepository;

    /**
     * 예시 레코드 한 건을 조회합니다.
     *
     * @param id 레코드 id
     * @return 계약 응답
     * @throws BusinessException {@link DefaultErrorCode#DEFAULT_NOT_FOUND} — 행이 없거나 소프트 삭제된 경우.
     *                           목이 {@code id}=2로 재현하는 사례가 실서버에서는 이 조건입니다.
     */
    public DefaultResponse getDefault(Integer id) {
        return defaultRepository.findByIdAndDeletedFalse(id)
                .map(DefaultResponse::from)
                .orElseThrow(() -> new BusinessException(DefaultErrorCode.DEFAULT_NOT_FOUND));
    }

    /**
     * 예시 레코드 목록을 페이지로 조회합니다.
     *
     * <p>계약의 {@code page}는 1부터, Spring Data는 0부터 시작하므로 여기서 한 번만 옮깁니다. 범위 검증(1 이상, 100 이하)은
     * 컨트롤러의 제약 애너테이션이 끝냈다고 전제합니다. 마지막 페이지를 넘어선 요청은 오류가 아니라 빈 목록입니다.
     *
     * @param page 1부터 시작하는 페이지 번호
     * @param size 한 페이지 항목 수
     * @param sort 정렬 기준
     * @return 항목과 페이지 정보
     */
    public DefaultListResponse getDefaults(int page, int size, DefaultSort sort) {
        Pageable pageable = PageRequest.of(page - 1, size, sort.toSort());
        return DefaultListResponse.of(defaultRepository.findActivePage(pageable), page);
    }
}
