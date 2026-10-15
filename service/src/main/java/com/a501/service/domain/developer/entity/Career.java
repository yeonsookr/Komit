package com.a501.service.domain.developer.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/** 개발자 경력 한 줄. 증빙 확인(R-DEV-01)은 운영이 하며, 개발자의 저장 요청으로는 확인 상태가 바뀌지 않습니다. */
@Getter
@Entity
@Table(name = "careers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Career extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "file_id")
    private Integer fileId;

    @Column(name = "company", nullable = false, length = 50)
    private String company;

    @Column(name = "role", nullable = false, length = 100)
    private String role;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "developer_id", nullable = false)
    private Integer developerId;

    @ColumnDefault("FALSE")
    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    public static Career create(Integer developerId, String company, String role, LocalDate startDate, LocalDate endDate, Integer evidenceFileId) {
        Career career = new Career();
        career.developerId = developerId;
        career.update(company, role, startDate, endDate, evidenceFileId);
        return career;
    }

    /**
     * 저장 요청의 값으로 맞춥니다. 확인을 마친 경력은 증빙과 표시 내용이 어긋나지 않도록 내용과 확인 상태를 모두 그대로 둡니다.
     */
    public void update(String company, String role, LocalDate startDate, LocalDate endDate, Integer evidenceFileId) {
        if (verified) {
            return;
        }
        this.company = company;
        this.role = role;
        this.startDate = startDate;
        this.endDate = endDate;
        this.fileId = evidenceFileId;
    }

    public void remove(LocalDateTime now) {
        markDeleted(now);
    }
}
