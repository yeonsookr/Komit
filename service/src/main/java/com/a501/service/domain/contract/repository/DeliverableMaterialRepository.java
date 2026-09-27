package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.DeliverableMaterial;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliverableMaterialRepository extends JpaRepository<DeliverableMaterial, Integer> {
    List<DeliverableMaterial> findAllByDeliverableIdInAndDeletedFalseOrderByIdAsc(Collection<Integer> deliverableIds);
}
