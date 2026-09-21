package com.a501.service.domain.developer.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.developer.dto.request.WritePortfolioItemRequest;
import com.a501.service.domain.developer.dto.response.MyPortfolioItemResponse;
import com.a501.service.domain.developer.entity.PortfolioItem;
import com.a501.service.domain.developer.repository.PortfolioItemRepository;
import com.a501.service.domain.file.service.FileLookupService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발자 작업물(포트폴리오). 순서는 등록순으로 고정이며 개발자 목록 카드에는 노출하지 않습니다.
 *
 * <p>저장은 전체 치환입니다. 요청에 없는 기존 줄은 소프트 삭제하고, 식별자가 null인 줄은 새로 만들며, 새 줄은 id가 커서 자연히
 * 뒤에 붙습니다. 썸네일이 있으면 저장소에서 짧게 사용할 수 있는 읽기 주소를 만들어 응답합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioItemService {
    private final PortfolioItemRepository portfolioItemRepository;
    private final FileLookupService fileLookupService;

    /** 내 프로필 편집 화면용 작업물. 등록순입니다. */
    public List<MyPortfolioItemResponse> listMine(Integer developerId) {
        List<PortfolioItem> items = portfolioItemRepository.findByDeveloperIdAndDeletedFalseOrderByIdAsc(developerId);
        Map<Integer, String> thumbnailUrls = fileLookupService.findReadUrls(
                items.stream().map(PortfolioItem::getThumbnailId).filter(Objects::nonNull).toList());
        return items.stream()
                // 썸네일 없는 줄은 조회하지 않습니다. 불변 Map은 null 키 조회에 NPE를 던집니다.
                .map(item -> MyPortfolioItemResponse.of(
                        item, item.getThumbnailId() == null ? null : thumbnailUrls.get(item.getThumbnailId())))
                .toList();
    }

    /**
     * 요청 목록으로 작업물 전체를 맞춥니다.
     *
     * @throws BusinessException {@code FORBIDDEN} 요청의 {@code portfolioItemId}가 이 개발자의 살아 있는 작업물이 아닐 때.
     *                           남의 것·지운 것·없는 것을 가르지 않습니다.
     *                           {@code FILE_NOT_ATTACHABLE} 썸네일이 호출자가 올린 완료 상태의 파일이 아닐 때
     */
    @Transactional
    public void replaceAll(Integer developerId, Integer userId, List<WritePortfolioItemRequest> requests, LocalDateTime now) {
        Map<Integer, PortfolioItem> existing = portfolioItemRepository
                .findByDeveloperIdAndDeletedFalseOrderByIdAsc(developerId).stream()
                .collect(Collectors.toMap(PortfolioItem::getId, Function.identity()));
        assertOwnRows(requests, existing);
        fileLookupService.assertAttachable(userId,
                requests.stream().map(WritePortfolioItemRequest::thumbnailFileId).filter(Objects::nonNull).toList());

        Map<Integer, PortfolioItem> remaining = new HashMap<>(existing);
        for (WritePortfolioItemRequest request : requests) {
            if (request.portfolioItemId() == null) {
                portfolioItemRepository.save(PortfolioItem.create(
                        developerId, request.title(), request.description(), request.linkUrl(), request.thumbnailFileId()));
                continue;
            }
            existing.get(request.portfolioItemId())
                    .update(request.title(), request.description(), request.linkUrl(), request.thumbnailFileId());
        }
        requests.stream().map(WritePortfolioItemRequest::portfolioItemId).filter(Objects::nonNull).forEach(remaining::remove);
        remaining.values().forEach(item -> item.remove(now));
    }

    private static void assertOwnRows(List<WritePortfolioItemRequest> requests, Map<Integer, PortfolioItem> existing) {
        boolean foreign = requests.stream()
                .map(WritePortfolioItemRequest::portfolioItemId)
                .filter(Objects::nonNull)
                .anyMatch(id -> !existing.containsKey(id));
        if (foreign) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
