package com.a501.service.domain.diagnosis.entity;

import com.a501.service.common.persistence.CreatedSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "inspections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inspection extends CreatedSoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "diagnosis_id", nullable = false)
    private Integer diagnosisId;

    @Column(name = "readiness_score")
    private Integer readinessScore;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;

    @ColumnDefault("1")
    @Column(name = "diagnosis_version", nullable = false)
    private Short diagnosisVersion = 1;

    /**
     * 올라온 검사 결과 한 건. 검사는 의뢰인 컴퓨터에서 이미 끝났고 서버는 그것을 받아 적을 뿐이라, 결과를 받은 시점에 점수와 검사 시각이 함께 정해진다.
     */
    public static Inspection of(
            Integer diagnosisId,
            Short diagnosisVersion,
            Integer readinessScore,
            LocalDateTime scannedAt) {
        Inspection entity = new Inspection();
        entity.diagnosisId = Objects.requireNonNull(diagnosisId, "diagnosisId");
        entity.diagnosisVersion = Objects.requireNonNull(diagnosisVersion, "diagnosisVersion");
        entity.readinessScore = readinessScore;
        entity.scannedAt = scannedAt;
        entity.status = "IN_PROGRESS";
        entity.statusGroup = "INSPECTION_STATUS";
        return entity;
    }
}
