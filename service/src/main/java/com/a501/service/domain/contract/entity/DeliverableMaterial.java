package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 산출물에 등록한 인계 자료 하나. 파일이거나 URL이다. 실제 파일 정보는 files가 가진다.
 *
 * <p>R-QA-06: 한 산출물에 URL·TEST 파일·PAID 파일을 함께, 각각 여러 개 등록한다. 등록할 때마다 행이 하나
 * 늘고 다른 자료는 그대로 남는다. 한 행에는 파일과 URL 중 정확히 하나만 있고, URL은 인수 시험 중 여는
 * 자료라 언제나 TEST다.
 */
@Getter
@Entity
@Table(name = "deliverable_materials", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_deliverable_materials_deliverable_file", columnNames = {"deliverable_id", "file_id"}),
        @UniqueConstraint(
                name = "uk_deliverable_materials_deliverable_url", columnNames = {"deliverable_id", "url"})},
        check = @CheckConstraint(name = "ck_deliverable_materials_file_or_url",
                constraint = "(file_id IS NULL) <> (url IS NULL) AND (file_id IS NOT NULL OR access_stage = 'TEST')"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliverableMaterial extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "deliverable_id", nullable = false)
    private Integer deliverableId;

    /** files.file_id 가 BIGINT 라 Long 으로 둔다. URL 자료면 비어 있다. */
    @Column(name = "file_id")
    private Long fileId;

    /** 인수 시험 중 방문할 주소. 파일 자료면 비어 있다. */
    @Column(name = "url", columnDefinition = "text")
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_stage", nullable = false, length = 50)
    private DeliverableAccessStage accessStage;

    /** 파일 자료. 열람 단계는 등록할 때 개발자가 정한다. */
    public static DeliverableMaterial ofFile(Integer deliverableId, Long fileId, DeliverableAccessStage accessStage) {
        DeliverableMaterial material = new DeliverableMaterial();
        material.deliverableId = Objects.requireNonNull(deliverableId, "deliverableId");
        material.fileId = Objects.requireNonNull(fileId, "fileId");
        material.accessStage = Objects.requireNonNull(accessStage, "accessStage");
        return material;
    }

    /** URL 자료. 인수 시험 중 여는 자료라 TEST로 둔다. */
    public static DeliverableMaterial ofUrl(Integer deliverableId, String url) {
        DeliverableMaterial material = new DeliverableMaterial();
        material.deliverableId = Objects.requireNonNull(deliverableId, "deliverableId");
        material.url = Objects.requireNonNull(url, "url");
        material.accessStage = DeliverableAccessStage.TEST;
        return material;
    }
}
