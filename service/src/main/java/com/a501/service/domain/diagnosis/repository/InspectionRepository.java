package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Inspection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<Inspection, Integer> {
    Optional<Inspection> findByIdAndDeletedFalse(Integer id);

    /**
     * 진단서의 준비도 점수와 검사 시각이 나오는 곳. 회차를 함께 건다. 재진단을 열면 새 회차에는 아직 검사가 없어 비어 있어야 하는데, 진단의 마지막 검사를 집으면 지난 회차 점수가 그대로 보인다.
     */
    Optional<Inspection> findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(
            Integer diagnosisId, Short diagnosisVersion);

    /**
     * 진단에서 검사가 있는 가장 최근 회차. 계약서에서 새로 합의한 요구사항을 붙일 곳이다.
     *
     * <p>위 진단서 조회와 반대로 지난 회차로 내려간다. 의뢰인이 재진단을 열고 결과를 아직 올리지 않았어도
     * 계약 협의는 멈추지 않아야 한다.
     */
    Optional<Inspection> findFirstByDiagnosisIdAndDeletedFalseOrderByDiagnosisVersionDesc(Integer diagnosisId);
}
