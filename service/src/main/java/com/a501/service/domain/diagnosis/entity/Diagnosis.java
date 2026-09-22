package com.a501.service.domain.diagnosis.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
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
@Table(name = "diagnoses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diagnosis extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Integer projectId;

    @Column(name = "user_input", length = 255)
    private String userInput;

    @ColumnDefault("1")
    @Column(name = "version", nullable = false)
    private Short version = 1;

    public static Diagnosis of(Integer projectId) {
        Diagnosis entity = new Diagnosis();
        entity.projectId = Objects.requireNonNull(projectId, "projectId");
        return entity;
    }

    /**
     * 재진단 회차를 연다. 진단은 프로젝트당 한 행이라 새 행을 만들지 않고 회차만 올린다. 지난 회차의 검사와 설문은 그대로 남는다. (R-SCAN-05)
     */
    public void startNextVersion() {
        version = (short) (version + 1);
    }
}
