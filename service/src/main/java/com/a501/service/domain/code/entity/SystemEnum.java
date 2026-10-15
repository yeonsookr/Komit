package com.a501.service.domain.code.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "system_enums")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemEnum {
    @EmbeddedId
    private SystemEnumId id;

    @Column(name = "code_name", nullable = false, length = 100)
    private String codeName;

    @ColumnDefault("0")
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @ColumnDefault("FALSE")
    @Column(name = "is_deprecated", nullable = false)
    private boolean deprecated = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta_json", columnDefinition = "json")
    private String metaJson;

    @MapsId("groupCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_code",
            referencedColumnName = "group_code",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_system_enum_groups_TO_system_enums_1"))
    private SystemEnumGroup group;
}
