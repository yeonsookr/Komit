package com.a501.service.domain.file.repository;

import com.a501.service.domain.file.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    List<StoredFile> findByFileIdInAndDeletedFalse(Collection<Long> fileIds);
}
