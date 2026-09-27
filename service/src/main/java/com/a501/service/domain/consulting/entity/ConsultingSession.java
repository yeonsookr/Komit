package com.a501.service.domain.consulting.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "consulting_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultingSession extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "consulting_agreement_id", nullable = false)
    private Integer consultingAgreementId;

    @Column(name = "chat_ref_id", nullable = false, length = 50)
    private String chatRefId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "json")
    private String summary;

    /** 두 녹음을 합친 전사 원문. 비어 있으면 아직 전사 전이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript", columnDefinition = "json")
    private String transcript;

    /** 녹음을 전사할 수 없다고 판정한 시각. 차 있으면 다시 전사하지 않는다. */
    @Column(name = "transcript_failed_at")
    private LocalDateTime transcriptFailedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /** 양쪽이 모여 처음 들어간 시각. 나갔다 다시 들어와도 처음 시각을 지킨다. (R-CON-09) */
    public void startConsulting(LocalDateTime startedAt) {
        if (this.startedAt == null) {
            this.startedAt = startedAt;
        }
    }

    /**
     * 세션을 마친다.
     *
     * <p>같은 요청이 다시 와도 최초 종료 시각이 바뀌지 않는다. 종료는 한 번만 확정된다. (R-CON-15)
     */
    public void completeConsulting(LocalDateTime endedAt) {
        if (this.endedAt == null) {
            this.endedAt = endedAt;
        }
    }

    public boolean isEnded() {
        return endedAt != null;
    }

    /**
     * 전사를 남깁니다.
     *
     * <p>이미 전사가 있거나 전사할 수 없다고 판정된 세션이면 그대로 둡니다. 같은 결과가 다시
     * 와도 처음 것이 남습니다.
     *
     * @return 이번에 남겼으면 참
     */
    public boolean recordTranscript(String transcript) {
        if (!awaitingTranscript()) {
            return false;
        }
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        return true;
    }

    /** 녹음을 전사할 수 없다고 판정합니다. 이 세션은 다시 전사하지 않습니다. */
    public boolean markTranscriptFailed(LocalDateTime failedAt) {
        if (!awaitingTranscript()) {
            return false;
        }
        this.transcriptFailedAt = Objects.requireNonNull(failedAt, "failedAt");
        return true;
    }

    /**
     * 요약을 남깁니다. 이미 있으면 그대로 둡니다.
     *
     * @return 이번에 남겼으면 참
     */
    public boolean recordSummary(String summary) {
        if (this.summary != null) {
            return false;
        }
        this.summary = Objects.requireNonNull(summary, "summary");
        return true;
    }

    /** 요청서에 반영한 후보를 표시한 요약으로 바꿉니다. 저장된 요약이 있을 때만 부릅니다. */
    public void markChangesApplied(String summary) {
        if (this.summary == null) {
            throw new IllegalStateException("반영할 요약이 없습니다: " + id);
        }
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    private boolean awaitingTranscript() {
        return transcript == null && transcriptFailedAt == null;
    }

    /**
     * 계약서에 적힌 세션 하나.
     *
     * <p>{@code chatRefId} 는 아직 비어 있습니다. 식별자가 있어야 지을 수 있는 값이라 저장한 뒤
     * {@link #assignChatRef()} 로 채웁니다.
     */
    public static ConsultingSession of(
            Integer consultingAgreementId, int price, int durationMinutes) {
        ConsultingSession session = new ConsultingSession();
        session.consultingAgreementId =
                Objects.requireNonNull(consultingAgreementId, "consultingAgreementId");
        session.price = price;
        session.durationMinutes = durationMinutes;
        session.chatRefId = "";
        return session;
    }

    /**
     * 식별자가 정해진 뒤 채팅 참조 키를 짓습니다.
     *
     * <p>방과 한 이름 공간을 쓰므로 앞자리로 가릅니다. 세션 대화는 방 대화와 따로 쌓입니다.
     * (`R-MATCH-06`)
     */
    public void assignChatRef() {
        this.chatRefId = "session-" + id + "-chat";
    }

    /** 협의 중에 다시 보낸 계약서로 내용을 바꿉니다. */
    public void rewrite(int price, int durationMinutes) {
        this.price = price;
        this.durationMinutes = durationMinutes;
    }
}
