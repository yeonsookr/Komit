package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.SurveyOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyOptionRepository extends JpaRepository<SurveyOption, Integer> {
    /** 문항별로 따로 읽으면 문항 수만큼 쿼리가 늘어난다. 한 번에 읽어 문항에 나눠 붙인다. */
    List<SurveyOption> findByQuestionIdInAndDeletedFalseOrderByIdAsc(
            Collection<Integer> questionIds);
}
