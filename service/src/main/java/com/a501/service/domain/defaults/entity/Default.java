package com.a501.service.domain.defaults.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기준 API가 읽는 예시 레코드. 테이블 {@code defaults}.
 *
 * <p><b>ERD에 없는 테이블입니다.</b> 목 명세가 "어떤 도메인의 식별자도 아니다"라고 정의한 기준 API를 실제 DB 조회로 보이기 위한
 * 본보기 전용 저장소이며, 화면 기능이 이 테이블을 참조하지 않습니다. ERD의 다른 테이블과 관계를 맺지 않으므로
 * ERD 담당자와 합의할 필드가 없고, 도메인 데이터가 필요해지는 순간 이 엔티티가 아니라 해당 도메인을 만들어야 합니다.
 *
 * <p>공통 감사·소프트 삭제 컬럼은 {@link SoftDeleteEntity}가 정합니다. 삭제된 행은 조회에서 제외되어 404로 나갑니다.
 */
@Getter
@Entity
@Table(name = "defaults")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Default extends SoftDeleteEntity {
    /** 목 명세 {@code DefaultResponse.message}의 최대 길이는 정해져 있지 않아 컬럼 길이만 넉넉히 둡니다. */
    static final int MESSAGE_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "message", nullable = false, length = MESSAGE_MAX_LENGTH)
    private String message;

    /**
     * 새 예시 레코드를 만듭니다. 시드({@code data.sql})와 테스트가 씁니다. 기준 API에는 생성 엔드포인트가 없습니다.
     *
     * @param message 응답에 그대로 실릴 문구. 비어 있을 수 없고 {@value #MESSAGE_MAX_LENGTH}자를 넘을 수 없습니다.
     * @return 저장 전의 새 엔티티
     * @throws IllegalArgumentException 문구가 비었거나 너무 길 때. 사용자 입력이 아니라 코드 오류이므로 도메인 예외를 쓰지 않습니다.
     */
    public static Default of(String message) {
        Objects.requireNonNull(message, "message");
        if (message.isBlank() || message.length() > MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException("message must be 1.." + MESSAGE_MAX_LENGTH + " characters");
        }
        Default entity = new Default();
        entity.message = message;
        return entity;
    }

    /**
     * 소프트 삭제합니다. 이후 단건·목록 조회에서 보이지 않습니다.
     *
     * @param deletedAt 삭제 시각(UTC)
     */
    public void delete(LocalDateTime deletedAt) {
        markDeleted(Objects.requireNonNull(deletedAt, "deletedAt"));
    }
}
