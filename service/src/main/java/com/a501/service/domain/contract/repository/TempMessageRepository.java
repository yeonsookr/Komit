package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.TempMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TempMessageRepository extends JpaRepository<TempMessage, Integer> {
    List<TempMessage> findAllByContractIdAndDeletedFalseOrderByIdAsc(Integer contractId);
}
