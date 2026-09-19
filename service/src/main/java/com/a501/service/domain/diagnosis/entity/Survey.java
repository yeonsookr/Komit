package com.a501.service.domain.diagnosis.entity;

import com.a501.service.common.persistence.CreatedSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(
        name = "surveys",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_surveys_diagnosis_version",
                columnNames = {"diagnosis_id", "diagnosis_version"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Survey extends CreatedSoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "diagnosis_id", nullable = false)
    private Integer diagnosisId;

    @ColumnDefault("1")
    @Column(name = "diagnosis_version", nullable = false)
    private Short diagnosisVersion = 1;

    @ColumnDefault("FALSE")
    @Column(name = "is_done", nullable = false)
    private boolean done = false;
}
