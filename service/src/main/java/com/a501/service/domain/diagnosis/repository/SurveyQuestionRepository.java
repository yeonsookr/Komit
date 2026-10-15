package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.SurveyQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Integer> {
    /** 화면에 보여줄 순서는 만든 순서다. 정렬 컬럼이 따로 없다. */
    List<SurveyQuestion> findBySurveyIdAndDeletedFalseOrderByIdAsc(Integer surveyId);
}
