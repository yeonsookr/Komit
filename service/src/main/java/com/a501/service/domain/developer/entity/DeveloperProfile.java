package com.a501.service.domain.developer.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 개발자 프로필. 개발자당 한 행이며, 만드는 경로는 내 프로필 저장({@code PUT /api/users/me/developer-profile}) 하나뿐입니다. */
@Getter
@Entity
@Table(name = "developer_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeveloperProfile extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id", nullable = false)
    private Integer profileId;

    @Column(name = "intro", nullable = false, length = 100)
    private String intro;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "years_of_experience", precision = 3, scale = 1)
    private BigDecimal yearsOfExperience;

    @Column(name = "developer_id", nullable = false)
    private Integer developerId;

    @Column(name = "field_code_id")
    private Integer fieldCodeId;

    @Column(name = "active_from")
    private LocalTime activeFrom;

    @Column(name = "active_to")
    private LocalTime activeTo;

    public static DeveloperProfile create(Integer developerId, String intro, String description, BigDecimal yearsOfExperience,
            Integer fieldCodeId, LocalTime activeFrom, LocalTime activeTo) {
        DeveloperProfile profile = new DeveloperProfile();
        profile.developerId = developerId;
        profile.update(intro, description, yearsOfExperience, fieldCodeId, activeFrom, activeTo);
        return profile;
    }

    public void update(String intro, String description, BigDecimal yearsOfExperience, Integer fieldCodeId,
            LocalTime activeFrom, LocalTime activeTo) {
        this.intro = intro;
        this.description = description;
        this.yearsOfExperience = yearsOfExperience;
        this.fieldCodeId = fieldCodeId;
        this.activeFrom = activeFrom;
        this.activeTo = activeTo;
    }
}
