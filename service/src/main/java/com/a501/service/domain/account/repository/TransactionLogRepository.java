package com.a501.service.domain.account.repository;

import com.a501.service.domain.account.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, Integer> {}
