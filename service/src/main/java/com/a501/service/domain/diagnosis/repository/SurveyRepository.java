package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Survey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyRepository extends JpaRepository<Survey, Integer> {
    Optional<Survey> findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(
            Integer diagnosisId, Short diagnosisVersion);

    /** 이어서 할 설문을 찾는다. 회차가 맞는지는 부르는 쪽이 가린다. */
    List<Survey> findByDiagnosisIdInAndDoneFalseAndDeletedFalse(Collection<Integer> diagnosisIds);
}
