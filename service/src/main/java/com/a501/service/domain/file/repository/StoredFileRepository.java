package com.a501.service.domain.file.repository;

import com.a501.service.domain.file.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {}
