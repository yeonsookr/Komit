package com.a501.service.domain.project.entity;

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

@Getter
@Entity
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    public static Project of(Integer clientId, String name, String description) {
        Project entity = new Project();
        entity.clientId = Objects.requireNonNull(clientId, "clientId");
        entity.name = Objects.requireNonNull(name, "name");
        entity.description = description;
        return entity;
    }
}
