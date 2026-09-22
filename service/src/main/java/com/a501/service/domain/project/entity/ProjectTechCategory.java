package com.a501.service.domain.project.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import com.a501.service.domain.code.entity.DynamicCode;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "project_tech_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTechCategory extends SoftDeleteEntity {
    @EmbeddedId
    private ProjectTechCategoryId id;

    @MapsId("projectId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            referencedColumnName = "id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_projects_TO_project_tech_categories_1"))
    private Project project;

    @MapsId("techCodeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "tech_code_id",
            referencedColumnName = "code_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_dynamic_codes_TO_project_tech_categories_1"))
    private DynamicCode techCode;

    public static ProjectTechCategory of(Project project, DynamicCode techCode) {
        ProjectTechCategory entity = new ProjectTechCategory();
        entity.project = Objects.requireNonNull(project, "project");
        entity.techCode = Objects.requireNonNull(techCode, "techCode");
        entity.id = new ProjectTechCategoryId(project.getId(), techCode.getCodeId());
        return entity;
    }
}
