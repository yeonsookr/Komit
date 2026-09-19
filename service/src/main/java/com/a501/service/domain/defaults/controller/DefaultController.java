package com.a501.service.domain.defaults.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.domain.defaults.dto.request.DefaultSort;
import com.a501.service.domain.defaults.dto.response.DefaultListResponse;
import com.a501.service.domain.defaults.dto.response.DefaultResponse;
import com.a501.service.domain.defaults.service.DefaultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기준 API 컨트롤러. 목 명세 {@code default} 태그의 두 엔드포인트를 계약 그대로 구현합니다.
 *
 * <p>컨트롤러는 파라미터를 받아 검증 애너테이션을 붙이고 서비스에 넘긴 뒤 DTO를 돌려주는 일만 합니다. 실패 응답은
 * 서비스가 던진 {@code BusinessException}과 검증 예외를 {@code GlobalExceptionHandler}가 바꾸므로 여기에는
 * {@code try/catch}도 {@code ResponseEntity.status(...)}도 없습니다.
 *
 * <p>두 엔드포인트 모두 인증이 필요합니다({@code SecurityConfig}의 기본 규칙). 목은 인증을 확인하지 않아 명세에 401이 없지만,
 * 실서버는 토큰이 없거나 만료되면 공통 401 {@code UNAUTHORIZED}를 돌려줍니다. 인증 사용자 정보는 소유권 판단이 없는 이 API에서
 * 쓸 곳이 없어 받지 않습니다. 필요한 컨트롤러는 {@code @AuthenticationPrincipal AuthenticatedUser user}로 받습니다.
 *
 * <p>페이지 파라미터의 범위 제약({@code @Min}·{@code @Max})은 본문 DTO와 달리 파라미터에 직접 붙입니다. Spring MVC가
 * 메서드 검증을 수행해 위반을 422로 넘깁니다. 기본값은 목과 같습니다. (BACKEND_CONVENTIONS 10.1)
 * 클래스에 {@code @Validated}를 붙이지 않습니다. 붙이면 이 내장 검증이 꺼지고 AOP 메서드 검증의
 * {@code ConstraintViolationException}이 되어 처리기가 500으로 내보냅니다.
 *
 * <p>{@code sort}처럼 계약 값이 enum 상수 이름이 될 수 없는 파라미터는 {@link #registerDefaultSortEditor(WebDataBinder)}가
 * 등록하는 {@link DefaultSortEditor}로 받습니다. {@code Converter} 빈을 쓰지 않는 이유는 그 클래스에 있습니다.
 */
@Tag(name = "default", description = "코밋 서비스 API의 응답 규약을 정하는 기준 API. 단건은 리소스를 그대로, 목록은 content와 페이지 정보로 감싼다.")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DefaultController {
    private final DefaultService defaultService;

    /**
     * 요청마다 {@link DefaultSort} 바인딩 에디터를 등록합니다. 핸들러가 아니므로 로깅 애스펙트 대상에서 빠집니다.
     *
     * @param binder 현재 요청의 데이터 바인더
     */
    @InitBinder
    void registerDefaultSortEditor(WebDataBinder binder) {
        binder.registerCustomEditor(DefaultSort.class, new DefaultSortEditor());
    }

    /**
     * 기본 응답 단건 조회.
     *
     * @param id 예시 레코드 id. 정수가 아니면 422입니다.
     * @return 200과 {@link DefaultResponse}
     */
    @Operation(
            summary = "기본 응답 단건 조회",
            description = "단건 조회 기준 API. 성공 응답은 공통 래퍼 없이 리소스를 그대로 반환하고, 실패는 HTTP 상태와 `code`로 구분한다.\n\n"
                    + "- **200**: 살아 있는 레코드가 있을 때\n"
                    + "- **404 / DEFAULT_NOT_FOUND**: 레코드가 없거나 삭제되었을 때. 타인의 리소스도 존재를 감추기 위해 같은 응답이다\n"
                    + "- **403 / FORBIDDEN**: 존재가 공개된 리소스에 권한이 없을 때. 이 API에는 발생 조건이 없어 문서상의 사례다\n"
                    + "- **422 / INVALID_REQUEST**: `id`가 정수가 아닐 때\n"
                    + "- **401 / UNAUTHORIZED**: 토큰이 없거나 만료되었을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 원인은 응답에 담지 않고 고정 문구만 반환한다",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "접근 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "리소스 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/default/{id}")
    DefaultResponse getDefault(
            @Parameter(description = "default 예시 id. 어떤 도메인의 식별자도 아니다.", example = "1")
            @PathVariable Integer id) {
        return defaultService.getDefault(id);
    }

    /**
     * 기본 응답 목록 조회.
     *
     * @param page 페이지 번호(1부터). 기본 1
     * @param size 페이지 크기(1~100). 기본 20
     * @param sort 정렬 기준. 기본 {@code id,asc}
     * @return 200과 {@link DefaultListResponse}
     */
    @Operation(
            summary = "기본 응답 목록 조회",
            description = "목록 조회 기준 API. 페이지네이션 파라미터와 목록 응답 형식을 이 API에 맞춘다.\n\n"
                    + "- **200**: 파라미터가 유효할 때. 마지막 페이지를 넘어선 `page`는 `content`가 빈 배열이고 나머지 페이지 정보는 그대로다\n"
                    + "- **422 / INVALID_REQUEST**: `page`가 1 미만, `size`가 1 미만이거나 100 초과, `sort`가 허용된 값이 아닐 때\n"
                    + "- **401 / UNAUTHORIZED**: 토큰이 없거나 만료되었을 때\n"
                    + "- **500 / INTERNAL_ERROR**: 원인은 응답에 담지 않고 고정 문구만 반환한다\n\n"
                    + "정렬은 `필드명,asc|desc` 형식이며 이 API는 `id`만 지원한다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "페이지네이션 파라미터 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/defaults")
    DefaultListResponse getDefaults(
            @Parameter(description = "페이지 번호. 1부터 시작한다.", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "한 페이지에 담을 항목 수. 최대 100이다.", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "정렬 기준. `필드명,asc|desc` 형식이며 이 API는 id만 지원한다.",
                    schema = @Schema(type = "string", allowableValues = {"id,asc", "id,desc"}, defaultValue = "id,asc"))
            @RequestParam(defaultValue = "id,asc") DefaultSort sort) {
        return defaultService.getDefaults(page, size, sort);
    }
}
