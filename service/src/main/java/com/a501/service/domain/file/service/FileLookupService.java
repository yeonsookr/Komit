package com.a501.service.domain.file.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.file.FileErrorCode;
import com.a501.service.domain.file.dto.request.UploadPurpose;
import com.a501.service.domain.file.entity.StoredFile;
import com.a501.service.domain.file.repository.StoredFileRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 파일을 리소스에 붙이기 전에 확인하는 창구입니다. 다른 도메인은 {@code StoredFileRepository}를 직접 쓰지 않고
 * 이 서비스만 호출합니다. (BACKEND_CONVENTIONS 7.1)
 *
 * <p>업로드 발급·완료 API는 파일 API 작업에서 붙습니다. 이 서비스는 파일을 붙일 수 있는지 판정하고, 원래 파일명과
 * 화면에서 잠시 사용할 수 있는 읽기 주소를 돌려줍니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileLookupService {
    /** {@code files.status} 코드값. 올리기 완료 검증을 통과한 파일만 어딘가에 붙일 수 있습니다. */
    private final StoredFileRepository storedFileRepository;
    private final ObjectStorageService objectStorageService;

    /**
     * 파일들이 모두 호출자가 올린 완료 상태의 파일인지 확인합니다. null은 "붙이지 않음"이라 건너뜁니다.
     *
     * @throws BusinessException {@link FileErrorCode#FILE_NOT_ATTACHABLE} 하나라도 없거나, 완료 전이거나, 남의 파일일 때
     */
    public void assertAttachable(Integer userId, Collection<Integer> fileIds) {
        Set<Long> wanted = ids(fileIds);
        if (wanted.isEmpty()) {
            return;
        }
        long attachable = storedFileRepository.findByFileIdInAndDeletedFalse(wanted).stream()
                .filter(file -> isAttachableBy(file, userId))
                .map(StoredFile::getFileId)
                .distinct()
                .count();
        if (attachable != wanted.size()) {
            throw new BusinessException(FileErrorCode.FILE_NOT_ATTACHABLE);
        }
    }

    /**
     * 그 용도로 올린, 호출자의 완료된 파일 하나인지 확인합니다.
     *
     * <p>용도를 따지는 이유는 다른 데 쓰려고 올린 파일이 엉뚱한 자리에 붙는 것을 막기 위해서입니다
     * (`R-FILE-02`). 프로필 사진을 세션 녹음으로 붙이면 전사가 읽지 못합니다.
     *
     * <p><b>용도가 비어 있는 파일은 거절합니다.</b> 이 칸이 생기기 전에 올린 파일이라 무엇으로
     * 올렸는지 알 수 없습니다.
     *
     * @param userId 붙이려는 사람
     * @param fileId 파일 id
     * @param expected 이 자리가 받는 용도
     * @throws BusinessException {@link FileErrorCode#FILE_NOT_ATTACHABLE} 없거나, 완료 전이거나,
     *     남의 파일이거나, 다른 용도로 올린 파일일 때
     */
    public void assertAttachable(Integer userId, Integer fileId, UploadPurpose expected) {
        StoredFile file = fileId == null
                ? null
                : storedFileRepository.findByFileIdAndDeletedFalse(fileId.longValue()).orElse(null);
        if (file == null
                || !isAttachableBy(file, userId)
                || !expected.name().equals(file.getPurpose())) {
            throw new BusinessException(FileErrorCode.FILE_NOT_ATTACHABLE);
        }
    }

    /** 파일 식별자별 원래 파일명. 없는 파일은 결과에 빠집니다. */
    public Map<Integer, String> findOriginalNames(Collection<Integer> fileIds) {
        Set<Long> wanted = ids(fileIds);
        if (wanted.isEmpty()) {
            // Map.of()는 null 키 조회에 NPE를 던지므로, 파일 없는 항목을 null로 조회하는 호출자도 안전한 빈 Map을 돌려줍니다.
            return Collections.emptyMap();
        }
        return storedFileRepository.findByFileIdInAndDeletedFalse(wanted).stream()
                .collect(Collectors.toMap(file -> file.getFileId().intValue(), StoredFile::getOriginalName,
                        (first, second) -> first));
    }

    /** 파일 식별자별 임시 읽기 주소. 없는 파일은 결과에 빠지며 저장소 주소는 한 번의 DB 조회 뒤 일괄 생성합니다. */
    public Map<Integer, String> findReadUrls(Collection<Integer> fileIds) {
        Set<Long> wanted = ids(fileIds);
        if (wanted.isEmpty()) {
            // Map.of()는 null 키 조회에 NPE를 던지므로, 파일 없는 항목을 null로 조회하는 호출자도 안전한 빈 Map을 돌려줍니다.
            return Collections.emptyMap();
        }
        return storedFileRepository.findByFileIdInAndDeletedFalse(wanted).stream()
                .filter(StoredFile::isCompleted)
                .collect(Collectors.toMap(
                        file -> file.getFileId().intValue(),
                        objectStorageService::issueReadUrl,
                        (first, second) -> first));
    }

    private static boolean isAttachableBy(StoredFile file, Integer userId) {
        return file.isCompleted() && Objects.equals(file.getUserId(), userId);
    }

    private static Set<Long> ids(Collection<Integer> fileIds) {
        if (fileIds == null) {
            return Set.of();
        }
        return fileIds.stream().filter(Objects::nonNull).map(Integer::longValue).collect(Collectors.toSet());
    }
}
