package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.TempMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TempMessageRepository extends JpaRepository<TempMessage, Integer> {}
