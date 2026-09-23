package com.a501.service.domain.room.entity;

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
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Entity
@Table(name = "rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends SoftDeleteEntity {
    private static final String TYPE_GROUP = "ROOM_TYPE";
    private static final String TYPE_INQUIRY = "INQUIRY";
    private static final String STATUS_GROUP = "ROOM_STATUS";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";

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

    /**
     * 추천에서 상담을 요청해 무료 상담방을 엽니다.
     *
     * <p>개발자의 수락을 기다리지 않습니다. 요청하면 그 자리에서 방이 열리고 개발자는 '받은 의뢰'에서
     * 그 방을 봅니다.
     *
     * <p>{@code chatRefId} 는 {@link #openFrom} 과 같은 이유로 비어 있습니다. 저장한 뒤
     * {@link #assignChatRef()} 로 채웁니다.
     *
     * @param recommendationItemId 이 방이 시작된 추천 항목. 직접 탐색으로 열었으면 {@code null}
     */
    public static Room openInquiry(
            Integer projectId, Integer developerId, Integer recommendationItemId) {
        Room room = new Room();
        room.projectId = Objects.requireNonNull(projectId, "projectId");
        room.developerId = Objects.requireNonNull(developerId, "developerId");
        room.recommendationItemId = recommendationItemId;
        room.chatRefId = "";
        room.type = TYPE_INQUIRY;
        room.typeGroup = TYPE_GROUP;
        room.status = STATUS_OPEN;
        room.statusGroup = STATUS_GROUP;
        return room;
    }

    /**
     * 갈래를 정해 이어질 방을 엽니다. 상담방과 같은 프로젝트·개발자를 그대로 물려받습니다.
     *
     * <p>{@code chatRefId} 는 아직 비어 있습니다. 식별자가 있어야 지을 수 있는 값이라 저장한
     * 뒤 {@link #assignChatRef()} 로 채웁니다.
     *
     * @param inquiry 갈래를 정한 상담방
     * @param type 이어갈 방 용도
     */
    public static Room openFrom(Room inquiry, String type) {
        Room room = new Room();
        room.projectId = inquiry.projectId;
        room.developerId = inquiry.developerId;
        room.recommendationItemId = inquiry.recommendationItemId;
        room.chatRefId = "";
        room.type = type;
        room.typeGroup = TYPE_GROUP;
        room.status = STATUS_OPEN;
        room.statusGroup = STATUS_GROUP;
        return room;
    }

    /** 식별자가 정해진 뒤 채팅 참조 키를 짓습니다. */
    public void assignChatRef() {
        this.chatRefId = "room-" + id + "-chat";
    }

    /**
     * 갈래가 정해져 상담이 마무리됩니다. 이어질 방을 가리키고 스스로는 닫힙니다.
     *
     * <p>나눈 이야기를 계속 볼 수 있어야 하므로 방은 지우지 않습니다. 닫기만 할 뿐 글을 막지는
     * 않습니다 — 상담방 잠금은 MVP 밖입니다. (R-MATCH-03)
     *
     * @param convertedRoomId 새로 열린 방
     */
    public void convertTo(Integer convertedRoomId) {
        this.convertedRoomId = convertedRoomId;
        this.status = STATUS_CLOSED;
    }

    /** 갈래를 정할 수 있는 방인지. 상담방이면서 아직 정하지 않았어야 합니다. */
    public boolean isConvertible() {
        return TYPE_INQUIRY.equals(type) && convertedRoomId == null;
    }
}
