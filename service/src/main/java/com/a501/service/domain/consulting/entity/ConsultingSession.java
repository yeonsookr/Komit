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
