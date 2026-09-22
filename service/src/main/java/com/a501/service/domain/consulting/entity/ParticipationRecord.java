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

    /**
     * 동의를 기록한다. 행이 생기는 것이 곧 동의다. (R-CON-17)
     *
     * <p>아직 들어간 것은 아니다. 상대도 동의해야 {@link #enter} 로 입장 시각이 찍힌다.
     */
    public static ParticipationRecord consent(
            Integer consultingSessionId,
            Integer userId,
            Short noticeVersion,
            LocalDateTime consentedAt) {
        ParticipationRecord record = new ParticipationRecord();
        record.consultingSessionId = consultingSessionId;
        record.userId = userId;
        record.noticeVersion = noticeVersion;
        record.consentedAt = consentedAt;
        return record;
    }

    /** 실제로 들어간 시각. 이미 들어와 있으면 처음 시각을 지킨다. */
    public void enter(LocalDateTime joinedAt) {
        if (this.joinedAt == null) {
            this.joinedAt = joinedAt;
        }
    }
}
