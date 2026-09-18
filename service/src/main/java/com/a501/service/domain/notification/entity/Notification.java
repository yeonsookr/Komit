package com.a501.service.domain.notification.entity;

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
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "content", nullable = false, length = 255)
    private String content;

    @ColumnDefault("FALSE")
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "target_id")
    private Integer targetId;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_type_group", nullable = false, length = 50)
    private String targetTypeGroup;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "type_group", nullable = false, length = 50)
    private String typeGroup;

    @Column(name = "title", nullable = false, length = 100)
    private String title;
}
