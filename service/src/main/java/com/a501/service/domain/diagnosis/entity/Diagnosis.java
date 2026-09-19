package com.a501.service.domain.diagnosis.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}
