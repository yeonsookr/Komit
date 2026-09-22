package com.a501.service.domain.developer.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.developer.dto.LowestPriceView;
import com.a501.service.domain.developer.dto.request.WritePriceRequest;
import com.a501.service.domain.developer.dto.response.PriceCatalogItemResponse;
import com.a501.service.domain.developer.dto.response.TaskPriceResponse;
import com.a501.service.domain.developer.entity.PriceCatalog;
import com.a501.service.domain.developer.repository.PriceCatalogRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개발자 가격표. 저장은 전체 치환입니다. 무료 상담은 가격표에 없는 고정 정책이라 여기 담지 않습니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceCatalogService {
    private final PriceCatalogRepository priceCatalogRepository;

    /** 가격표 전부. 컨설팅이 먼저 오고 그 안에서 싼 것부터입니다. */
    public List<PriceCatalogItemResponse> list(Integer developerId) {
        return priceCatalogRepository.findByDeveloperIdAndDeletedFalseOrderByTaskTypeAscPriceAscIdAsc(developerId).stream()
                .map(PriceCatalogItemResponse::from)
                .toList();
    }

    /**
     * 여러 개발자의 작업 종류별 최저가. 목록 카드의 {@code prices}입니다. 가격표를 등록하지 않은 종류는 빠지고, 하나도 없는 개발자는
     * 결과에 없습니다. 종류는 컨설팅({@code CONSULT})이 개발({@code DEVELOP})보다 먼저입니다.
     */
    public Map<Integer, List<TaskPriceResponse>> findLowestPrices(Collection<Integer> developerIds) {
        if (developerIds == null || developerIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<TaskPriceResponse>> prices = new HashMap<>();
        for (LowestPriceView view : priceCatalogRepository.findLowestPrices(developerIds)) {
            prices.computeIfAbsent(view.getDeveloperId(), id -> new ArrayList<>())
                    .add(new TaskPriceResponse(view.getTaskType(), view.getLowestPrice()));
        }
        return prices;
    }

    /**
     * 요청 목록으로 가격표 전체를 맞춥니다.
     *
     * @throws BusinessException {@code FORBIDDEN} 요청의 {@code priceId}가 이 개발자의 살아 있는 줄이 아닐 때
     */
    @Transactional
    public void replaceAll(Integer developerId, List<WritePriceRequest> requests, LocalDateTime now) {
        Map<Integer, PriceCatalog> existing = priceCatalogRepository
                .findByDeveloperIdAndDeletedFalseOrderByTaskTypeAscPriceAscIdAsc(developerId).stream()
                .collect(Collectors.toMap(PriceCatalog::getId, Function.identity()));
        boolean foreign = requests.stream()
                .map(WritePriceRequest::priceId)
                .filter(Objects::nonNull)
                .anyMatch(id -> !existing.containsKey(id));
        if (foreign) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        Map<Integer, PriceCatalog> remaining = new HashMap<>(existing);
        for (WritePriceRequest request : requests) {
            Short periodDays = request.periodDays() == null ? null : request.periodDays().shortValue();
            if (request.priceId() == null) {
                priceCatalogRepository.save(PriceCatalog.create(
                        developerId, request.taskType().name(), request.taskName(), request.price(), periodDays));
                continue;
            }
            existing.get(request.priceId())
                    .update(request.taskType().name(), request.taskName(), request.price(), periodDays);
        }
        requests.stream().map(WritePriceRequest::priceId).filter(Objects::nonNull).forEach(remaining::remove);
        remaining.values().forEach(row -> row.remove(now));
    }
}
