package com.a501.service.domain.developer.service;

import com.a501.service.domain.code.dto.CodeResponse;
import com.a501.service.domain.code.service.CodeQueryService;
import com.a501.service.domain.contract.service.ContractQueryService;
import com.a501.service.domain.developer.dto.DeveloperSearchRow;
import com.a501.service.domain.developer.dto.response.DeveloperSummaryResponse;
import com.a501.service.domain.developer.repository.DeveloperRepository;
import com.a501.service.domain.developer.dto.response.RatingResponse;
import com.a501.service.domain.developer.dto.response.TaskPriceResponse;
import com.a501.service.domain.file.service.FileLookupService;
import com.a501.service.domain.room.service.RoomQueryService;
import com.a501.service.domain.user.dto.UserProfileView;
import com.a501.service.domain.user.service.UserService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발자 조회 결과({@link DeveloperSearchRow})를 목록 한 줄({@link DeveloperSummaryResponse})로 조립합니다.
 *
 * <p>이름·사진은 사용자 도메인에, 사진의 임시 주소는 파일 도메인에, 분야·기술 이름은 코드 도메인에, 마친 계약 수는 방·계약 도메인에 식별자 목록으로 묻고 여기서
 * 합칩니다. 다른 도메인의 엔티티나 저장소는 쓰지 않으며, 한 페이지에 대해 도메인마다 한 번씩만 조회합니다. (BACKEND_CONVENTIONS 7.1)
 *
 * <p>사용자 도메인이 돌려주지 않은(탈퇴한) 사용자의 개발자는 결과에서 뺍니다. 탈퇴 회원의 이름과 프로필을 노출하지 않기 위해서입니다.
 * (R-ACC-07) 탈퇴 처리가 개발자 행을 함께 소프트 삭제하면 이 분기는 실행될 일이 없습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeveloperSummaryAssembler {
    /** 컨설팅·개발 계약이 마쳐진 상태의 코드값. {@code CONSULTING_AGREEMENT_STATUS}·{@code DEVELOPMENT_CONTRACT_STATUS} 둘 다 같습니다. */
    static final String COMPLETED = "COMPLETED";

    private final UserService userService;
    private final FileLookupService fileLookupService;
    private final CodeQueryService codeQueryService;
    private final DeveloperRepository developerRepository;
    private final CareerService careerService;
    private final PriceCatalogService priceCatalogService;
    private final DeveloperTechCategoryService techCategoryService;
    private final RoomQueryService roomQueryService;
    private final ContractQueryService contractQueryService;

    /** 조회 결과 순서를 그대로 지키며 조립합니다. 마친 계약 수는 여기서 셉니다. */
    public List<DeveloperSummaryResponse> assemble(List<DeveloperSearchRow> rows) {
        return assemble(rows, null);
    }

    /**
     * 식별자로 조립합니다. 목록을 거치지 않고 몇 명을 집어 보여줄 때 씁니다.
     *
     * <p>순서는 보장하지 않고, 없거나 프로필이 없는 개발자는 결과에서 빠집니다. 부르는 쪽이 자기 순서로 다시 세웁니다.
     */
    public List<DeveloperSummaryResponse> assembleByIds(Collection<Integer> developerIds) {
        return assemble(developerRepository.findRows(developerIds));
    }

    /**
     * 조회 결과 순서를 그대로 지키며 조립합니다.
     *
     * @param contractCounts 이미 세어 둔 개발자별 마친 계약 수. null이면 여기서 셉니다. 계약 많은 순 정렬처럼 서비스가 먼저 센 값을
     *                       다시 세지 않기 위한 것입니다.
     */
    public List<DeveloperSummaryResponse> assemble(List<DeveloperSearchRow> rows, Map<Integer, Long> contractCounts) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Integer> developerIds = rows.stream().map(DeveloperSearchRow::developerId).toList();
        Map<Integer, UserProfileView> users = userService.findProfiles(
                rows.stream().map(DeveloperSearchRow::userId).toList());
        Map<Integer, String> imageUrls = fileLookupService.findReadUrls(
                users.values().stream().map(UserProfileView::profileImageFileId).filter(Objects::nonNull).toList());
        Map<Integer, List<Integer>> techCodeIds = techCategoryService.listCodeIdsByDevelopers(developerIds);
        Map<Integer, CodeResponse> codes = codeQueryService.findCodes(codeIds(rows, techCodeIds));
        Set<Integer> verified = careerService.findVerifiedDeveloperIds(developerIds);
        Map<Integer, List<TaskPriceResponse>> prices = priceCatalogService.findLowestPrices(developerIds);
        Map<Integer, Long> contracts = contractCounts == null ? countCompletedContracts(developerIds) : contractCounts;

        List<DeveloperSummaryResponse> result = new ArrayList<>(rows.size());
        for (DeveloperSearchRow row : rows) {
            UserProfileView user = users.get(row.userId());
            if (user == null) {
                continue;
            }
            result.add(new DeveloperSummaryResponse(
                    row.developerId(),
                    user.name(),
                    user.profileImageFileId(),
                    profileImageUrl(user, imageUrls),
                    row.intro(),
                    row.fieldCodeId() == null ? null : codes.get(row.fieldCodeId()),
                    row.yearsOfExperience(),
                    verified.contains(row.developerId()),
                    RatingResponse.of(row.devRating(), row.devReviewCount()),
                    RatingResponse.of(row.explanatoryRating(), row.explanatoryReviewCount()),
                    techCodeIds.getOrDefault(row.developerId(), List.of()).stream()
                            .map(codes::get).filter(Objects::nonNull).toList(),
                    prices.getOrDefault(row.developerId(), List.of()),
                    Math.toIntExact(contracts.getOrDefault(row.developerId(), 0L))));
        }
        return result;
    }

    /**
     * 개발자별 마친 계약 수. 방 도메인에 개발자의 방을, 계약 도메인에 그 방의 마친 계약 수를 식별자로 물어 합칩니다.
     * 컨설팅과 개발을 합쳐 세며 진행 중이거나 파기된 계약은 들지 않습니다.
     */
    public Map<Integer, Long> countCompletedContracts(Collection<Integer> developerIds) {
        if (developerIds == null || developerIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> roomDevelopers = roomQueryService.findRoomDevelopers(developerIds);
        if (roomDevelopers.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Long> completedByRoom = contractQueryService.countCompletedByRooms(roomDevelopers.keySet());
        Map<Integer, Long> result = new HashMap<>();
        completedByRoom.forEach((roomId, count) -> result.merge(roomDevelopers.get(roomId), count, Long::sum));
        return result;
    }

    /** 직접 올린 사진이 있으면 그 임시 주소, 없으면 카카오 사진 주소. 둘 다 없으면 null입니다. 마이페이지 계정 칸과 같은 규칙입니다. */
    private static String profileImageUrl(UserProfileView user, Map<Integer, String> imageUrls) {
        if (user.profileImageFileId() == null) {
            return user.kakaoProfileImageUrl();
        }
        return imageUrls.getOrDefault(user.profileImageFileId(), user.kakaoProfileImageUrl());
    }

    private static Set<Integer> codeIds(List<DeveloperSearchRow> rows, Map<Integer, List<Integer>> techCodeIds) {
        Set<Integer> codeIds = new HashSet<>();
        rows.stream().map(DeveloperSearchRow::fieldCodeId).filter(Objects::nonNull).forEach(codeIds::add);
        techCodeIds.values().forEach(codeIds::addAll);
        return codeIds;
    }
}
