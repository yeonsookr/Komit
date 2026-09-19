package com.a501.service.domain.developer.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "developer_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeveloperProfile extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id", nullable = false)
    private Integer profileId;

    @Column(name = "intro", nullable = false, length = 100)
    private String intro;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "years_of_experience", precision = 3, scale = 1)
    private BigDecimal yearsOfExperience;

    @Column(name = "developer_id", nullable = false)
    private Integer developerId;

    @Column(name = "field_code_id")
    private Integer fieldCodeId;

    @Column(name = "active_from")
    private LocalTime activeFrom;

    @Column(name = "active_to")
    private LocalTime activeTo;
}
