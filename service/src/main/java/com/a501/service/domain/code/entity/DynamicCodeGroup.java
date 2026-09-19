package com.a501.service.domain.code.entity;

import com.a501.service.common.persistence.CreatedAtEntity;
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
@Table(name = "dynamic_code_groups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DynamicCodeGroup extends CreatedAtEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id", nullable = false)
    private Integer groupId;

    @Column(name = "group_key", nullable = false, length = 50)
    private String groupKey;

    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;

    @Column(name = "description", length = 255)
    private String description;

    @ColumnDefault("TRUE")
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
