package com.a501.service.domain.diagnosis.entity;

import com.a501.service.common.persistence.CreatedSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "survey_options")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyOption extends CreatedSoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "content", nullable = false, length = 255)
    private String content;

    @ColumnDefault("FALSE")
    @Column(name = "is_selected", nullable = false)
    private boolean selected = false;

    @Column(name = "question_id", nullable = false)
    private Integer questionId;

    public static SurveyOption of(Integer questionId, String content) {
        SurveyOption entity = new SurveyOption();
        entity.questionId = Objects.requireNonNull(questionId, "questionId");
        entity.content = Objects.requireNonNull(content, "content");
        return entity;
    }

    /** 주관식 답을 담는 행. 답이 있다는 것 자체가 선택이라 고른 상태로 만든다. */
    public static SurveyOption ofWrittenAnswer(Integer questionId, String content) {
        SurveyOption entity = of(questionId, content);
        entity.selected = true;
        return entity;
    }

    public void select() {
        selected = true;
    }

    public void deselect() {
        selected = false;
    }

    /** 주관식 답을 고쳐 쓴다. 객관식 보기 문구에는 쓰지 않는다. */
    public void rewrite(String content) {
        this.content = Objects.requireNonNull(content, "content");
        selected = true;
    }
}
