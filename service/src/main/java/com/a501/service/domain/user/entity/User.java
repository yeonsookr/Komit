package com.a501.service.domain.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "profile_image_file_id")
    private Integer profileImageFileId;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 25)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "kakao_id", nullable = false, unique = true)
    private Long kakaoId;

    @Column(name = "kakao_profile_image_url", length = 500)
    private String kakaoProfileImageUrl;

    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "is_email_valid")
    private Boolean emailValid;

    @Column(name = "kakao_connected_at")
    private Instant kakaoConnectedAt;

    @Getter(AccessLevel.NONE)
    @Column(name = "kakao_refresh_token", length = 255)
    private String kakaoRefreshToken;

    @Column(name = "kakao_refresh_token_expires_at")
    private Instant kakaoRefreshTokenExpiresAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "unlinked_at")
    private Instant unlinkedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Getter(AccessLevel.NONE)
    @Column(name = "has_dev_profile", nullable = false)
    private boolean hasDevProfile;

    @Column(name = "record_status", nullable = false, length = 50)
    private String recordStatus;

    @Column(name = "record_status_group", nullable = false, length = 50)
    private String recordStatusGroup;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public static User register(
            Long kakaoId,
            String nickname,
            String profileImageUrl,
            String email,
            boolean emailVerified,
            Boolean emailValid,
            Instant connectedAt,
            String protectedKakaoRefreshToken,
            Instant kakaoRefreshTokenExpiresAt,
            Instant now) {
        User user = new User();
        user.kakaoId = kakaoId;
        user.name = nickname;
        user.kakaoProfileImageUrl = profileImageUrl;
        user.email = email;
        user.emailVerified = emailVerified;
        user.emailValid = emailValid;
        user.kakaoConnectedAt = connectedAt;
        user.kakaoRefreshToken = protectedKakaoRefreshToken;
        user.kakaoRefreshTokenExpiresAt = kakaoRefreshTokenExpiresAt;
        user.lastLoginAt = now;
        user.hasDevProfile = false;
        user.recordStatus = "ACTIVE";
        user.recordStatusGroup = "RECORD_STATUS";
        user.deleted = false;
        return user;
    }

    public void recordKakaoLogin(
            String profileImageUrl,
            String email,
            boolean emailVerified,
            Boolean emailValid,
            Instant connectedAt,
            String protectedKakaoRefreshToken,
            Instant kakaoRefreshTokenExpiresAt,
            Instant now) {
        this.kakaoProfileImageUrl = profileImageUrl;
        this.email = email;
        this.emailVerified = emailVerified;
        this.emailValid = emailValid;
        this.kakaoConnectedAt = connectedAt;
        this.kakaoRefreshToken = protectedKakaoRefreshToken;
        this.kakaoRefreshTokenExpiresAt = kakaoRefreshTokenExpiresAt;
        this.lastLoginAt = now;
    }

    public boolean isWithdrawn() {
        return deleted || deletedAt != null || unlinkedAt != null;
    }

    public boolean hasDevProfile() {
        return hasDevProfile;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
