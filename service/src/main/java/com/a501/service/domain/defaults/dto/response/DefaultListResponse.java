package com.a501.service.domain.defaults.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

/**
 * 목록 조회 성공 응답. 목 명세 {@code DefaultListResponse}와 같습니다.
 *
 * <p>목록만 {@code content}와 페이지 정보로 감싸고 단건은 감싸지 않는다는 규약의 기준입니다. 페이지 번호는 계약대로
 * 1부터 시작하며, Spring Data의 0부터 시작하는 번호는 서비스 안에서만 씁니다.
 *
 * @param content       현재 페이지의 항목. 마지막 페이지를 넘어선 요청이면 빈 배열이고 나머지 값은 그대로 채웁니다.
 * @param page          요청한 페이지 번호(1부터).
 * @param size          한 페이지에 담을 최대 항목 수. 실제 항목 수가 아니라 요청한 크기입니다.
 * @param totalElements 조건에 맞는 전체 항목 수.
 * @param totalPages    전체 페이지 수. 항목이 하나도 없으면 0입니다.
 */
@Schema(description = "목록 조회 성공 응답. 목록에 한해 content와 페이지 정보로 감싼다.")
public record DefaultListResponse(
        @Schema(description = "현재 페이지에 속한 항목. 결과가 없으면 빈 배열이다.") List<DefaultResponse> content,
        @Schema(description = "현재 페이지 번호. 1부터 시작한다.", example = "1") int page,
        @Schema(description = "한 페이지에 담긴 최대 항목 수.", example = "20") int size,
        @Schema(description = "조건에 해당하는 전체 항목 수.", example = "137") long totalElements,
        @Schema(description = "전체 페이지 수. 전체 항목이 없으면 0이다.", example = "7") int totalPages) {

    /**
     * Spring Data 페이지 결과를 계약 형태로 바꿉니다.
     *
     * @param found 0부터 시작하는 번호로 조회한 페이지 결과
     * @param page  클라이언트가 요청한 1부터 시작하는 페이지 번호. {@code found.getNumber() + 1}과 같아야 합니다.
     * @return 계약 응답
     */
    public static DefaultListResponse of(Page<DefaultResponse> found, int page) {
        return new DefaultListResponse(
                found.getContent(),
                page,
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages());
    }
}
