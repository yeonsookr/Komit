package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "deliverables")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deliverable extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "development_contract_id", nullable = false)
    private Integer developmentContractId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "file_id")
    private Integer fileId;

    @ColumnDefault("true")
    @Column(name = "is_contractual", nullable = false)
    private boolean contractual = true;

    /** 넘겨받을 인계물 하나. 파일과 전달 시각은 개발자가 결과물을 올릴 때 채운다. */
    public static Deliverable of(Integer developmentContractId, String title) {
        Deliverable entity = new Deliverable();
        entity.developmentContractId = Objects.requireNonNull(developmentContractId, "developmentContractId");
        entity.title = Objects.requireNonNull(title, "title");
        return entity;
    }

    /** 계약서를 다시 써서 빠진 인계물. */
    public void removeFromContract(LocalDateTime removedAt) {
        markDeleted(removedAt);
    }
}
