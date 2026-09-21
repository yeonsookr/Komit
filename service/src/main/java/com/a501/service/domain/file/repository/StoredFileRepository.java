package com.a501.service.domain.file.repository;

import com.a501.service.domain.file.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    List<StoredFile> findByFileIdInAndDeletedFalse(Collection<Long> fileIds);

    Optional<StoredFile> findByFileIdAndDeletedFalse(Long fileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from StoredFile f where f.fileId = :fileId and f.deleted = false")
    Optional<StoredFile> findByFileIdForUpdate(@Param("fileId") Long fileId);
}
