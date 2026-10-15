package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "temp_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TempMessage extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "content", nullable = false, length = 300)
    private String content;

    @Column(name = "contract_id", nullable = false)
    private Integer contractId;

    @ColumnDefault("FALSE")
    @Column(name = "is_client", nullable = false)
    private boolean client = false;

    /** 계약서와 함께 남기는 전달 메시지. 누가 남겼는지로 화면의 좌우가 갈립니다. */
    public static TempMessage of(Integer contractId, String content, boolean client) {
        TempMessage message = new TempMessage();
        message.contractId = Objects.requireNonNull(contractId, "contractId");
        message.content = Objects.requireNonNull(content, "content");
        message.client = client;
        return message;
    }
}
