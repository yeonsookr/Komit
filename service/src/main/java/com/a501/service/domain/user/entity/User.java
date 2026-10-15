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

    /** 개발자 프로필을 처음 만들 때 개발자 역할을 더합니다. 의뢰인 역할은 그대로 남습니다. (R-ACC-08) */
    public void markDevProfile() {
        this.hasDevProfile = true;
    }

    public boolean hasDevProfile() {
        return hasDevProfile;
    }

    /**
     * 탈퇴합니다. 행을 지우지 않고 삭제 표시만 켭니다. 거래 상대의 화면에도 남는 기록이 이 행을 가리키기 때문입니다.
     * 표시가 켜지면 {@link #isWithdrawn()}이 참이 되어 로그인과 재가입이 막힙니다. (R-ACC-06, R-AUTH-05)
     */
    public void withdraw(LocalDateTime now) {
        markDeleted(now);
    }

    /**
     * 탈퇴한 계정에서 사람을 알아볼 수 있는 값과 카카오 연결 정보를 지웁니다. 행과 삭제 표시는 남겨 거래 기록이 계속 가리키게 합니다. (R-ACC-06)
     *
     * <p>{@code kakao_id}는 비울 수 없는 유일 컬럼이라 {@code -id}로 바꿉니다. 카카오 회원번호는 양수라 어떤 계정과도 겹치지 않고,
     * 원래 번호로 되돌릴 수 없으며, 같은 카카오 계정이 새로 가입할 자리가 비워집니다. (R-AUTH-05) 이름도 비울 수 없어 화면이 쓰는 문구로 바꿉니다.
     *
     * @param withdrawnName 탈퇴한 사람의 이름 자리에 들어갈 문구
     * @return 이번에 지웠으면 참, 이미 지운 계정이라 바꾼 것이 없으면 거짓
     */
    public boolean anonymize(String withdrawnName) {
        if (!isDeleted() || isAnonymized()) {
            return false;
        }
        this.kakaoId = (long) -id;
        this.name = withdrawnName;
        this.email = null;
        this.emailVerified = false;
        this.emailValid = null;
        this.kakaoProfileImageUrl = null;
        this.profileImageFileId = null;
        this.kakaoConnectedAt = null;
        this.kakaoRefreshToken = null;
        this.kakaoRefreshTokenExpiresAt = null;
        return true;
    }

    /** 식별 정보를 이미 지운 계정인지. 지우면 {@code kakao_id}가 음수가 됩니다. */
    public boolean isAnonymized() {
        return kakaoId != null && kakaoId < 0;
    }

    /** 화면에 표시하는 이름을 바꿉니다. 카카오 닉네임과는 따로 가며, 다음 카카오 로그인이 되돌리지 않습니다. */
    public void rename(String name) {
        this.name = name;
    }

    /** 직접 올린 사진을 프로필 사진으로 겁니다. 카카오 사진 주소는 지우지 않고 그대로 둡니다. */
    public void changeProfileImage(Integer fileId) {
        this.profileImageFileId = fileId;
    }

    private static LocalDateTime utc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
