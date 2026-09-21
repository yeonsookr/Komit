package com.a501.service.domain.consulting.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(
        name = "participation_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_participation_records_session_user",
                columnNames = {"consulting_session_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipationRecord extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "consulting_session_id", nullable = false)
    private Integer consultingSessionId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "consented_at", nullable = false)
    private LocalDateTime consentedAt;

    @ColumnDefault("1")
    @Column(name = "notice_version", nullable = false)
    private Short noticeVersion = 1;

    @Column(name = "file_id")
    private Long fileId;
}
