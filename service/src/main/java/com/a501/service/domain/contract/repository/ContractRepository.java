package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.Contract;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, Integer> {
    Optional<Contract> findByIdAndDeletedFalse(Integer id);

    Optional<Contract> findByRoomIdAndDeletedFalse(Integer roomId);

    List<Contract> findByRoomIdInAndDeletedFalse(Collection<Integer> roomIds);
}
