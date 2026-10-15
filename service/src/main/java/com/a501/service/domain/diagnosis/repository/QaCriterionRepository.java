package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.QaCriterion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QaCriterionRepository extends JpaRepository<QaCriterion, Integer> {
    List<QaCriterion> findAllByIdInAndDeletedFalse(Collection<Integer> ids);

    /**
     * 요청서의 확인 문장. 결함마다 따로 읽으면 결함 수만큼 쿼리가 늘어난다. 한 번에 읽어 결함에 나눠 붙인다.
     *
     * <p><b>계약서의 확인 문장은 뺀다.</b> 같은 요구사항에 함께 붙어 있어도 요청서에 섞이면 안 되고, 요청서를
     * 저장할 때 「요청서에서 지운 문장」으로 처리되어도 안 된다. 이름은 두고 조건을 여기 적는다.
     */
    @Query("""
            select q from QaCriterion q
            where q.issueId in :issueIds and q.contract = false and q.deleted = false
            order by q.id asc
            """)
    List<QaCriterion> findByIssueIdInAndDeletedFalseOrderByIdAsc(@Param("issueIds") Collection<Integer> issueIds);
}
