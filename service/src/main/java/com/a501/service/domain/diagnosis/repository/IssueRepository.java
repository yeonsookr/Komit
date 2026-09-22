package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Issue;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRepository extends JpaRepository<Issue, Integer> {
    List<Issue> findAllByIdInAndDeletedFalse(Collection<Integer> ids);

    /**
     * 요청서에 남아 있는 요구사항 수.
     *
     * <p>해결로 내린 것은 요청서에서 빠지고(R-REQ-02), 계약에 들어간 것은 그대로 남습니다.
     * 지난 판의 것은 세지 않습니다(R-SCAN-05).
     */
    @Query(
            """
            select count(i)
            from Issue i, Inspection n
            where n.id = i.inspectionId
              and n.diagnosisId = :diagnosisId
              and n.diagnosisVersion = :version
              and i.status <> 'RESOLVED'
              and i.deleted = false
              and n.deleted = false
            """)
    long countInRequest(@Param("diagnosisId") Integer diagnosisId, @Param("version") Short version);
}
