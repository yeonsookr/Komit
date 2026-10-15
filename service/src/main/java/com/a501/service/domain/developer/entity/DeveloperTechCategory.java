package com.a501.service.domain.developer.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개발자가 다룰 수 있는 기술 하나. {@code (developer_id, tech_code_id)}가 자연키라 같은 기술을 지웠다 다시 넣으면 새 행 대신
 * 소프트 삭제된 행을 되살립니다. 기술 이름은 코드 도메인 서비스에 묻고, 여기서는 식별자만 가집니다. (BACKEND_CONVENTIONS 7.1)
 */
@Getter
@Entity
@Table(name = "developer_tech_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeveloperTechCategory extends SoftDeleteEntity {
    @EmbeddedId
    private DeveloperTechCategoryId id;

    public static DeveloperTechCategory of(Integer developerId, Integer techCodeId) {
        DeveloperTechCategory row = new DeveloperTechCategory();
        row.id = new DeveloperTechCategoryId(developerId, techCodeId);
        return row;
    }

    public Integer getTechCodeId() {
        return id.getTechCodeId();
    }

    public void remove(LocalDateTime now) {
        markDeleted(now);
    }

    public void reactivate() {
        restore();
    }
}
