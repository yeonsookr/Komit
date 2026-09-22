package com.a501.service.domain.room.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Entity
@Table(name = "rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "project_id", nullable = false)
    private Integer projectId;

    @Column(name = "chat_ref_id", nullable = false, length = 50)
    private String chatRefId;

    @CreationTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @Column(name = "developer_id", nullable = false)
    private Integer developerId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "type_group", nullable = false, length = 50)
    private String typeGroup;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;

    @Column(name = "recommendation_item_id")
    private Integer recommendationItemId;

    /**
     * 갈래를 정해 새로 열린 방. 상담방에서만 찹니다.
     *
     * <p>방의 용도는 따로 담지 않습니다. 가리키는 방의 {@code type} 을 읽으면 되고, 그래야
     * 그 방이 지워졌을 때 식별자와 용도가 함께 비어 화면의 전제가 깨지지 않습니다.
     */
    @Column(name = "converted_room_id")
    private Integer convertedRoomId;
}
