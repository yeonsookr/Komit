package com.a501.service.domain.user.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "profile_image_file_id")
    private Integer profileImageFileId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "name", nullable = false, length = 25)
    private String name;

    @Column(name = "kakao_id", nullable = false, unique = true)
    private Long kakaoId;

    @Column(name = "kakao_profile_image_url", length = 500)
    private String kakaoProfileImageUrl;

    @ColumnDefault("FALSE")
    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "is_email_valid")
    private Boolean emailValid;

    @Column(name = "kakao_connected_at")
    private LocalDateTime kakaoConnectedAt;

    @Getter(AccessLevel.NONE)
    @Column(name = "kakao_refresh_token", length = 255)
    private String kakaoRefreshToken;

    @Column(name = "kakao_refresh_token_expires_at")
    private LocalDateTime kakaoRefreshTokenExpiresAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "unlinked_at")
    private LocalDateTime unlinkedAt;

    @ColumnDefault("FALSE")
    @Column(name = "has_dev_profile", nullable = false)
    private boolean hasDevProfile = false;

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
        user.kakaoConnectedAt = utc(connectedAt);
        user.kakaoRefreshToken = protectedKakaoRefreshToken;
        user.kakaoRefreshTokenExpiresAt = utc(kakaoRefreshTokenExpiresAt);
        user.lastLoginAt = utc(now);
        user.hasDevProfile = false;
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
        this.kakaoConnectedAt = utc(connectedAt);
        this.kakaoRefreshToken = protectedKakaoRefreshToken;
        this.kakaoRefreshTokenExpiresAt = utc(kakaoRefreshTokenExpiresAt);
        this.lastLoginAt = utc(now);
    }

    public boolean isWithdrawn() {
        return isDeleted() || getDeletedAt() != null || unlinkedAt != null;
    }

    public boolean hasDevProfile() {
        return hasDevProfile;
    }

    private static LocalDateTime utc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
