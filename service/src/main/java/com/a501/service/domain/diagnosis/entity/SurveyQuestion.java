package com.a501.service.domain.diagnosis.entity;

import com.a501.service.common.persistence.CreatedSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import com.a501.service.domain.diagnosis.SurveyAnswerType;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "survey_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyQuestion extends CreatedSoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "survey_id", nullable = false)
    private Integer surveyId;

    @Column(name = "content", nullable = false, length = 255)
    private String content;

    @Column(name = "answer_type", nullable = false, length = 50)
    private String answerType;

    @Column(name = "answer_type_group", nullable = false, length = 50)
    private String answerTypeGroup;

    public static SurveyQuestion of(Integer surveyId, String content, SurveyAnswerType answerType) {
        SurveyQuestion entity = new SurveyQuestion();
        entity.surveyId = Objects.requireNonNull(surveyId, "surveyId");
        entity.content = Objects.requireNonNull(content, "content");
        entity.answerType = Objects.requireNonNull(answerType, "answerType").name();
        entity.answerTypeGroup = SurveyAnswerType.GROUP;
        return entity;
    }

    public SurveyAnswerType answerType() {
        return SurveyAnswerType.valueOf(answerType);
    }
}
