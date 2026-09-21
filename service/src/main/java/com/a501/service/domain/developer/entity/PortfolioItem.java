package com.a501.service.domain.developer.entity;

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

/**
 * 개발자가 올린 작업물 하나. 순서를 따로 저장하지 않고 등록순({@code id} 오름차순)으로 고정합니다. 개발자 목록 카드에는 노출하지 않습니다.
 */
@Getter
@Entity
@Table(name = "portfolio_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioItem extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "thumbnail_id")
    private Integer thumbnailId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "link_url", length = 2048)
    private String linkUrl;

    @Column(name = "developer_id", nullable = false)
    private Integer developerId;

    public static PortfolioItem create(Integer developerId, String title, String description, String linkUrl, Integer thumbnailId) {
        PortfolioItem item = new PortfolioItem();
        item.developerId = developerId;
        item.update(title, description, linkUrl, thumbnailId);
        return item;
    }

    /** 저장 요청의 값으로 맞춥니다. 썸네일은 같은 식별자면 유지, 다른 식별자면 교체, null이면 제거입니다. */
    public void update(String title, String description, String linkUrl, Integer thumbnailId) {
        this.title = title;
        this.description = description;
        this.linkUrl = linkUrl;
        this.thumbnailId = thumbnailId;
    }

    /** 저장 요청 목록에서 빠진 작업물을 지운 것으로 봅니다. */
    public void remove(LocalDateTime now) {
        markDeleted(now);
    }
}
