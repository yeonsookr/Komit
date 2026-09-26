package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Issue;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRepository extends JpaRepository<Issue, Integer>, IssueRepositoryCustom {
    Optional<Issue> findByIdAndDeletedFalse(Integer id);

    List<Issue> findAllByIdInAndDeletedFalse(Collection<Integer> ids);

    /**
     * 이 검사의 요구사항. 요청서 저장, 추천 입력, 재진단 비교가 쓴다.
     *
     * <p><b>계약서에서 만든 요구사항은 뺀다.</b> 요청서는 계약서가 건드리지 않는다. 이름으로 만든 조회에
     * 조건을 더하면 이름이 바뀌어 부르는 곳을 모두 고쳐야 해서, 이름은 두고 조건을 여기 적는다.
     */
    @Query("""
            select i from Issue i
            where i.inspectionId = :inspectionId and i.contract = false and i.deleted = false
            order by i.id asc
            """)
    List<Issue> findByInspectionIdAndDeletedFalseOrderByIdAsc(@Param("inspectionId") Integer inspectionId);

    /** 진단서에 적는 요구사항 수. 계약서에서 만든 것은 진단 결과가 아니라 세지 않는다. */
    @Query("""
            select count(i) from Issue i
            where i.inspectionId = :inspectionId and i.contract = false and i.deleted = false
            """)
    long countByInspectionIdAndDeletedFalse(@Param("inspectionId") Integer inspectionId);

    /** 진단서에 적는 상태별 요구사항 수. 계약서에서 만든 것은 세지 않는다. */
    @Query("""
            select count(i) from Issue i
            where i.inspectionId = :inspectionId and i.status = :status
              and i.contract = false and i.deleted = false
            """)
    long countByInspectionIdAndDeletedFalseAndStatus(
            @Param("inspectionId") Integer inspectionId, @Param("status") String status);

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
              and i.contract = false
              and i.deleted = false
            """)
    long countResolvedAt(
            @Param("diagnosisId") Integer diagnosisId, @Param("version") Short version);

    /**
     * 요청서에 남아 있는 요구사항 수.
     *
     * <p>해결로 내린 것은 요청서에서 빠지고(R-REQ-02), 계약에 들어간 것은 그대로 남습니다.
     * 지난 판의 것은 세지 않습니다(R-SCAN-05). 계약서에서 만든 것은 요청서에 없어 세지 않습니다.
     */
    @Query(
            """
            select count(i)
            from Issue i, Inspection n
            where n.id = i.inspectionId
              and n.diagnosisId = :diagnosisId
              and n.diagnosisVersion = :version
              and i.status <> 'RESOLVED'
              and i.contract = false
              and i.deleted = false
              and n.deleted = false
            """)
    long countInRequest(@Param("diagnosisId") Integer diagnosisId, @Param("version") Short version);
}
