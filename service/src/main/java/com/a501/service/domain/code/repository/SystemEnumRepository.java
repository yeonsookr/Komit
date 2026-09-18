package com.a501.service.domain.code.repository;

import com.a501.service.domain.code.entity.SystemEnum;
import com.a501.service.domain.code.entity.SystemEnumId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemEnumRepository extends JpaRepository<SystemEnum, SystemEnumId> {}
