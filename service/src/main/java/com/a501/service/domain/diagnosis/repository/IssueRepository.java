package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Issue;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRepository extends JpaRepository<Issue, Integer>, IssueRepositoryCustom {
    List<Issue> findAllByIdInAndDeletedFalse(Collection<Integer> ids);

    List<Issue> findByInspectionIdAndDeletedFalseOrderByIdAsc(Integer inspectionId);

    long countByInspectionIdAndDeletedFalse(Integer inspectionId);

    long countByInspectionIdAndDeletedFalseAndStatus(Integer inspectionId, String status);

    /**
     * 지난 회차에 있었는데 이번 회차에서 사라진 결함 수. 해결이 확인된 결함은 그것을 발견한 지난 회차 검사에 매달려 있어, 이번 회차 검사 하나만 보면 세지 못한다.
     */
    @Query("""
            select count(i)
            from Issue i
            where i.inspectionId in (
                    select p.id from Inspection p
                    where p.diagnosisId = :diagnosisId and p.deleted = false)
              and i.fixedVersion = :version
              and i.deleted = false
            """)
    long countResolvedAt(
            @Param("diagnosisId") Integer diagnosisId, @Param("version") Short version);
}
