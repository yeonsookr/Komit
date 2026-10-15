package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.Deliverable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliverableRepository extends JpaRepository<Deliverable, Integer> {
    List<Deliverable> findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
            Integer developmentContractId);

    Optional<Deliverable> findByIdAndDeletedFalse(Integer id);
}
