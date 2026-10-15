package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.Contract;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ContractRepository extends JpaRepository<Contract, Integer> {
    Optional<Contract> findByIdAndDeletedFalse(Integer id);

    /**
     * 서명과 취소를 줄 세우려고 계약 행을 잠그고 가져옵니다.
     *
     * <p>의뢰인의 취소와 개발자의 서명이 겹치면, 둘 다 「아직 협의중」을 보고 하나는 취소를, 하나는 체결을
     * 적습니다. 잠그면 뒤에 온 쪽이 앞의 결과를 보고 멈춥니다. 양쪽이 동시에 서명할 때 한쪽 서명이 덮어쓰이는
     * 것도 함께 막습니다. (`R-CON-01`, `R-CON-21`)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Contract> findWithLockByIdAndDeletedFalse(Integer id);

    Optional<Contract> findByRoomIdAndDeletedFalse(Integer roomId);

    List<Contract> findByRoomIdInAndDeletedFalse(Collection<Integer> roomIds);
}
