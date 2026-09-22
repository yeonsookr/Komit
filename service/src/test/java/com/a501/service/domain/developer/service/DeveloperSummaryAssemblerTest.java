package com.a501.service.domain.developer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.developer.dto.DeveloperSearchRow;
import com.a501.service.domain.developer.dto.response.DeveloperSummaryResponse;
import com.a501.service.domain.developer.dto.response.RatingResponse;
import com.a501.service.domain.developer.dto.response.TaskPriceResponse;
import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.room.service.RoomQueryService;
import com.a501.service.domain.user.dto.UserProfileView;
import com.a501.service.domain.user.service.UserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 다른 도메인에 식별자로 물어 목록 한 줄을 조립하는 규칙을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class DeveloperSummaryAssemblerTest {
    @Mock
    private UserService userService;
    @Mock
    private FileLookupService fileLookupService;
    @Mock
    private CodeQueryService codeQueryService;
    @Mock
    private CareerService careerService;
    @Mock
    private PriceCatalogService priceCatalogService;
    @Mock
    private DeveloperTechCategoryService techCategoryService;
    @Mock
    private RoomQueryService roomQueryService;
    @Mock
    private ContractQueryService contractQueryService;
    @InjectMocks
    private DeveloperSummaryAssembler assembler;

    @Test
    @DisplayName("이름·사진 주소·분야·기술·증빙·최저가·계약 수를 도메인마다 한 번씩 물어 목 예시와 같은 줄을 만든다")
    void assemblesSummaryFromDomains() {
        DeveloperSearchRow lee = new DeveloperSearchRow(3, 103, "간단한 상담도 받습니다.", 42, new BigDecimal("5.0"),
                null, null, null, 4.4, 2, 4.9, 2);
        DeveloperSearchRow rookie = new DeveloperSearchRow(9, 109, "처음입니다.", null, null, null, null, null, null, 0, null, 0);
        when(userService.findProfiles(List.of(103, 109))).thenReturn(Map.of(
                103, new UserProfileView(103, "이준", null, null),
                109, new UserProfileView(109, "새내기", 77, "https://k.kakaocdn.net/109.jpg")));
        // 직접 올린 사진(77)만 파일 도메인에 묻고, 이준은 사진이 없어 카카오 주소도 없으니 null이다.
        when(fileLookupService.findReadUrls(List.of(77))).thenReturn(Map.of(77, "https://cdn.komit.example.com/profile/77.png"));
        when(techCategoryService.listCodeIdsByDevelopers(List.of(3, 9))).thenReturn(Map.of(3, List.of(21, 23)));
        when(codeQueryService.findCodes(Set.of(42, 21, 23))).thenReturn(Map.of(
                42, new CodeResponse(42, "DB 연결"), 21, new CodeResponse(21, "Next.js"), 23, new CodeResponse(23, "TypeScript")));
        when(careerService.findVerifiedDeveloperIds(List.of(3, 9))).thenReturn(Set.of(3));
        when(priceCatalogService.findLowestPrices(List.of(3, 9))).thenReturn(Map.of(3, List.of(
                new TaskPriceResponse("CONSULT", 50000L), new TaskPriceResponse("DEVELOP", 100000L))));
        when(roomQueryService.findRoomDevelopers(List.of(3, 9))).thenReturn(Map.of(91, 3, 92, 3, 93, 9));
        when(contractQueryService.countCompletedByRooms(Set.of(91, 92, 93))).thenReturn(Map.of(91, 20L, 92, 3L));

        List<DeveloperSummaryResponse> result = assembler.assemble(List.of(lee, rookie));

        assertThat(result).containsExactly(
                new DeveloperSummaryResponse(3, "이준", null, null, "간단한 상담도 받습니다.", new CodeResponse(42, "DB 연결"),
                        new BigDecimal("5.0"), true, RatingResponse.of(4.4, 2), RatingResponse.of(4.9, 2),
                        List.of(new CodeResponse(21, "Next.js"), new CodeResponse(23, "TypeScript")),
                        List.of(new TaskPriceResponse("CONSULT", 50000L), new TaskPriceResponse("DEVELOP", 100000L)), 23),
                new DeveloperSummaryResponse(9, "새내기", 77, "https://cdn.komit.example.com/profile/77.png", "처음입니다.", null, null, false,
                        RatingResponse.of(null, 0), RatingResponse.of(null, 0), List.of(), List.of(), 0));
    }

    @Test
    @DisplayName("사용자 도메인이 돌려주지 않은(탈퇴한) 사용자의 개발자는 결과에서 빠지고, 빈 입력은 아무 도메인도 부르지 않는다")
    void withdrawnUsersAreDroppedAndEmptyInputIsCheap() {
        DeveloperSearchRow gone = new DeveloperSearchRow(5, 105, "소개", null, null, null, null, null, null, 0, null, 0);
        when(userService.findProfiles(List.of(105))).thenReturn(Map.of());
        when(roomQueryService.findRoomDevelopers(List.of(5))).thenReturn(Map.of());

        assertThat(assembler.assemble(List.of(gone))).isEmpty();
        assertThat(assembler.assemble(List.of())).isEmpty();
        verify(contractQueryService, never()).countCompletedByRooms(anyCollection());
        verify(userService).findProfiles(any());
    }

    @Test
    @DisplayName("미리 센 계약 수를 넘기면 방·계약 도메인을 다시 부르지 않는다")
    void precomputedContractCountsSkipContractDomain() {
        DeveloperSearchRow lee = new DeveloperSearchRow(3, 103, "소개", null, null, null, null, null, null, 0, null, 0);
        when(userService.findProfiles(List.of(103))).thenReturn(Map.of(103, new UserProfileView(103, "이준", null, null)));

        List<DeveloperSummaryResponse> result = assembler.assemble(List.of(lee), Map.of(3, 41L));

        assertThat(result).singleElement().extracting(DeveloperSummaryResponse::contractCount).isEqualTo(41);
        verify(roomQueryService, never()).findRoomDevelopers(anyCollection());
        verify(contractQueryService, never()).countCompletedByRooms(anyCollection());
    }
}
