package com.a501.service.domain.developer.controller;

import com.a501.service.common.exception.ErrorResponse;
import com.a501.service.domain.developer.dto.request.DeveloperSort;
import com.a501.service.domain.developer.dto.request.SearchMode;
import com.a501.service.domain.developer.dto.response.DeveloperDetailResponse;
import com.a501.service.domain.developer.dto.response.DeveloperListResponse;
import com.a501.service.domain.developer.service.DeveloperQueryService;
import com.a501.service.domain.review.dto.response.ReviewListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 남에게 보이는 개발자 API. 목 명세 {@code 3 개발자 찾기 · 상담방} 태그의 {@code /api/developers} 세 엔드포인트입니다.
 *
 * <p>세 엔드포인트 모두 로그인 없이 부를 수 있습니다({@code SecurityConfig}가 GET을 열어 둠). 랜딩의 '증상별로 찾기'로 바로
 * 들어오는 경로가 있어서입니다. 인증 사용자를 판단에 쓰지 않으므로 받지 않습니다.
 *
 * <p>페이지 파라미터의 범위 제약은 파라미터에 직접 붙이고 클래스에 {@code @Validated}를 붙이지 않습니다. 계약 값이 상수 이름이 될 수
 * 없는 {@code searchMode}·{@code sort}는 {@link #registerEditors(WebDataBinder)}가 등록하는 에디터로 받습니다.
 * (BACKEND_CONVENTIONS 9, 10.1)
 */
@Tag(name = "3 개발자 찾기 · 상담방", description = "의뢰인이 직접 개발자를 찾고 공개 프로필과 리뷰를 본다")
@RestController
@RequestMapping("/api/developers")
@RequiredArgsConstructor
public class DeveloperController {
    private final DeveloperQueryService developerQueryService;

    /** 요청마다 계약 값 enum의 바인딩 에디터를 등록합니다. 핸들러가 아니므로 로깅 애스펙트 대상에서 빠집니다. */
    @InitBinder
    void registerEditors(WebDataBinder binder) {
        binder.registerCustomEditor(DeveloperSort.class, new DeveloperSortEditor());
        binder.registerCustomEditor(SearchMode.class, new SearchModeEditor());
    }

    @Operation(
            summary = "개발자 찾기",
            description = "의뢰인이 직접 개발자를 찾는다. 추천을 거치지 않고 스스로 고르는 경로다. 로그인하지 않아도 부를 수 있다.\n\n"
                    + "검색은 두 가지다. `problem`은 겪고 있는 문제를 문장으로 적어 맞는 개발자를 찾고, `name`은 이름으로 바로 찾는다. "
                    + "`problem` 검색은 문장의 뜻을 읽어야 하므로 AI 서버가 붙기 전까지 목과 같이 거르지 않고 전체를 준다.\n\n"
                    + "`totalDevelopers`는 조건을 걸지 않았을 때의 전체 수라, 화면의 '128명 중 5명'에서 앞 숫자에 해당한다. "
                    + "카드 머리는 `field`와 `yearsOfExperience`, 칩은 `techCategories`다. `page`는 1부터 센다.\n\n"
                    + "- **200**: 항상. 조건에 맞는 사람이 없으면 `content`가 빈 배열이다\n"
                    + "- **422 / INVALID_REQUEST**: `searchMode`나 `sort`가 허용된 값이 아니거나 `page`·`size`가 범위를 벗어날 때\n"
                    + "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    DeveloperListResponse findDevelopers(
            @Parameter(description = "검색 방식. `problem`은 문제 문장으로 찾고, `name`은 개발자 이름으로 찾는다.",
                    schema = @Schema(type = "string", allowableValues = {"problem", "name"}, defaultValue = "problem"))
            @RequestParam(defaultValue = "problem") SearchMode searchMode,
            @Parameter(description = "검색어. 비우면 전체에서 고른다.", example = "예약 버튼이 아무 반응 없어요")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "분야 코드 식별자. 여럿이면 `?fieldCodeIds=41&fieldCodeIds=42`처럼 같은 이름을 거듭 적는다. "
                    + "DEV_FIELD의 항목이 아닌 값은 조용히 무시한다.",
                    array = @ArraySchema(schema = @Schema(type = "integer", example = "41")))
            @RequestParam(required = false) List<Integer> fieldCodeIds,
            @Parameter(description = "정렬 기준. 차례로 실력 높은 순, 설명력 높은 순, 비용 낮은 순, 계약 많은 순이다. "
                    + "비용은 가격표 전체의 최저가로 견준다.",
                    schema = @Schema(type = "string",
                            allowableValues = {"devRating,desc", "explanatoryRating,desc", "price,asc", "contract,desc"},
                            defaultValue = "devRating,desc"))
            @RequestParam(defaultValue = "devRating,desc") DeveloperSort sort,
            @Parameter(description = "페이지 번호. 1부터 시작한다.", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "한 페이지 항목 수. 최대 100이다.", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return developerQueryService.search(searchMode, keyword, fieldCodeIds, sort, page, size);
    }

    @Operation(
            summary = "개발자 프로필 조회",
            description = "개발자 한 명의 소개, 경력, 작업물, 평점, 가격표를 한 번에 내려준다. 로그인하지 않아도 볼 수 있는 공개 프로필이다.\n\n"
                    + "경력은 `isVerified`로 갈린다. 무료 상담은 가격표에 없는 고정 정책이라 이 응답에 담기지 않는다. "
                    + "리뷰 총 건수는 `devRating.reviewCount`다.\n\n"
                    + "- **200**: 개발자가 있을 때\n"
                    + "- **404 / DEVELOPER_NOT_FOUND**: 없거나 탈퇴한 개발자다. 목은 `developerId`=404로 재현한다\n"
                    + "- **422 / INVALID_REQUEST**: `developerId`가 정수가 아닐 때\n"
                    + "- **500 / INTERNAL_ERROR**: 프로필을 읽지 못했을 때")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "개발자 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{developerId}")
    DeveloperDetailResponse getDeveloper(
            @Parameter(description = "개발자 식별자. 사용자 식별자가 아니다.", example = "3")
            @PathVariable Integer developerId) {
        return developerQueryService.getDetail(developerId);
    }

    @Operation(
            summary = "개발자 리뷰 목록 조회",
            description = "이 개발자가 계약을 마친 뒤 받은 리뷰를 최신순으로 내려준다. 로그인하지 않아도 볼 수 있다.\n\n"
                    + "의뢰인 이름은 서버가 가려서 내보낸다. 어느 프로젝트의 리뷰인지는 담지 않으며 계약 종류만 `contractType`으로 알려준다. "
                    + "`totalElements`는 개발자 프로필의 `devRating.reviewCount`와 언제나 같다. `page`는 1부터 센다.\n\n"
                    + "- **200**: 개발자가 있을 때. 받은 리뷰가 없으면 `content`가 빈 배열이다\n"
                    + "- **404 / DEVELOPER_NOT_FOUND**: 없거나 탈퇴한 개발자다\n"
                    + "- **422 / INVALID_REQUEST**: `page`나 `size`가 범위를 벗어날 때\n"
                    + "- **500 / INTERNAL_ERROR**: 목록을 만들지 못했을 때")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "개발자 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "요청 스키마 유효성 검증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{developerId}/reviews")
    ReviewListResponse getDeveloperReviews(
            @Parameter(description = "개발자 식별자.", example = "3")
            @PathVariable Integer developerId,
            @Parameter(description = "페이지 번호. 1부터 시작한다.", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "한 페이지 항목 수. 최대 100이다.", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return developerQueryService.getReviews(developerId, page, size);
    }
}
