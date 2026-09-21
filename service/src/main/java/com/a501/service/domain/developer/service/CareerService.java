package com.a501.service.domain.developer.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.developer.dto.request.WriteCareerRequest;
import com.a501.service.domain.developer.dto.response.MyCareerResponse;
import com.a501.service.domain.developer.entity.Career;
import com.a501.service.domain.developer.repository.CareerRepository;
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
 * 개발자 경력. 저장은 전체 치환이며, 확인을 마친 경력의 확인 표시와 확인 시각은 저장해도 그대로 남습니다. (R-DEV-01)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareerService {
    private final CareerRepository careerRepository;
    private final FileLookupService fileLookupService;

    /** 내 프로필 편집 화면용 경력. 최근 경력이 먼저이며 확인 전 증빙은 파일명을 함께 내려줍니다. */
    public List<MyCareerResponse> listMine(Integer developerId) {
        List<Career> careers = careerRepository.findByDeveloperIdAndDeletedFalseOrderByStartDateDescIdDesc(developerId);
        Map<Integer, String> names = fileLookupService.findOriginalNames(
                careers.stream().filter(career -> !career.isVerified()).map(Career::getFileId).filter(Objects::nonNull).toList());
        return careers.stream()
                .map(career -> MyCareerResponse.of(career, names.get(career.getFileId())))
                .toList();
    }

    /** 증빙이 확인된 경력을 하나라도 가지고 있는지. */
    public boolean hasVerified(Integer developerId) {
        return careerRepository.findByDeveloperIdAndDeletedFalseOrderByStartDateDescIdDesc(developerId).stream()
                .anyMatch(Career::isVerified);
    }

    /**
     * 요청 목록으로 경력 전체를 맞춥니다.
     *
     * @throws BusinessException {@code FORBIDDEN} 요청의 {@code careerId}가 이 개발자의 살아 있는 경력이 아닐 때,
     *                           {@code FILE_NOT_ATTACHABLE} 확인 전 경력의 증빙이 호출자가 올린 완료 상태의 파일이 아닐 때
     */
    @Transactional
    public void replaceAll(Integer developerId, Integer userId, List<WriteCareerRequest> requests, LocalDateTime now) {
        Map<Integer, Career> existing = careerRepository
                .findByDeveloperIdAndDeletedFalseOrderByStartDateDescIdDesc(developerId).stream()
                .collect(Collectors.toMap(Career::getId, Function.identity()));
        boolean foreign = requests.stream()
                .map(WriteCareerRequest::careerId)
                .filter(Objects::nonNull)
                .anyMatch(id -> !existing.containsKey(id));
        if (foreign) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        // 확인을 마친 경력은 증빙 원본이 이미 삭제되어 요청의 식별자를 쓰지 않으므로 검증 대상에서 뺍니다.
        fileLookupService.assertAttachable(userId, requests.stream()
                .filter(request -> request.careerId() == null || !existing.get(request.careerId()).isVerified())
                .map(WriteCareerRequest::evidenceFileId)
                .filter(Objects::nonNull)
                .toList());

        Map<Integer, Career> remaining = new HashMap<>(existing);
        for (WriteCareerRequest request : requests) {
            if (request.careerId() == null) {
                careerRepository.save(Career.create(developerId, request.company(), request.role(),
                        request.startDateValue(), request.endDateValue(), request.evidenceFileId()));
                continue;
            }
            existing.get(request.careerId()).update(request.company(), request.role(),
                    request.startDateValue(), request.endDateValue(), request.evidenceFileId());
        }
        requests.stream().map(WriteCareerRequest::careerId).filter(Objects::nonNull).forEach(remaining::remove);
        remaining.values().forEach(career -> career.remove(now));
    }
}
