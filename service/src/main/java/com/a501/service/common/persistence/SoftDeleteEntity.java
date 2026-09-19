package com.a501.service.common.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@MappedSuperclass
public abstract class SoftDeleteEntity extends AuditedEntity {
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "record_status", nullable = false, length = 50)
    private String recordStatus = "ACTIVE";

    @Column(name = "record_status_group", nullable = false, length = 50)
    private String recordStatusGroup = "RECORD_STATUS";

    @ColumnDefault("false")
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    /**
     * 레코드를 소프트 삭제 상태로 바꿉니다. 실제 행은 지우지 않고 {@code is_deleted}·{@code deleted_at}·{@code record_status}를 함께 바꿉니다.
     * 세 값이 따로 놀지 않도록 하위 엔티티는 필드를 직접 만지지 않고 이 메서드만 씁니다.
     *
     * @param deletedAt 삭제 시각(UTC). 하위 엔티티가 의도가 드러나는 이름의 메서드({@code withdraw()} 등)로 감싸 호출합니다.
     */
    protected void markDeleted(LocalDateTime deletedAt) {
        this.deleted = true;
        this.deletedAt = deletedAt;
        this.recordStatus = "DELETED";
    }
}
