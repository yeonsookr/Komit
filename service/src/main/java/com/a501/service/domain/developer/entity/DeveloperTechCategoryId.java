package com.a501.service.domain.developer.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeveloperTechCategoryId implements Serializable {
    @Column(name = "developer_id", nullable = false)
    private Integer developerId;

    @Column(name = "tech_code_id", nullable = false)
    private Integer techCodeId;
}
