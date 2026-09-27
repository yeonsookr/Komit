package com.a501.service.domain.contract.repository;

import com.a501.service.domain.contract.entity.Milestone;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MilestoneRepository extends JpaRepository<Milestone, Integer> {
    List<Milestone> findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc(
            Integer developmentContractId);

    Optional<Milestone> findByIdAndDeletedFalse(Integer id);

    /**
     * 작업이 속한 계약 id. 작업을 읽기 전에 계약 행부터 잠그려고 id 만 가져온다.
     *
     * <p>작업을 먼저 읽고 잠그면, 기다리는 동안 다른 요청이 바꾼 상태를 보지 못한다.
     */
    @Query("""
            select development.contractId from Milestone milestone, DevelopmentContract development
            where development.id = milestone.developmentContractId
              and milestone.id = :milestoneId and milestone.deleted = false and development.deleted = false
            """)
    Optional<Integer> findContractIdByMilestoneId(@Param("milestoneId") Integer milestoneId);

    /** 이 시각 이전에 확인을 요청하고 아직 답이 없는 작업. 자동 통과 대상이다. (R-QA-02) */
    @Query("""
            select milestone.id from Milestone milestone
            where milestone.status = 'SUBMITTED' and milestone.submittedAt <= :submittedBefore
              and milestone.deleted = false
            order by milestone.id
            """)
    List<Integer> findSubmittedIdsBefore(@Param("submittedBefore") LocalDateTime submittedBefore);
}
