package com.a501.service.domain.review.entity;

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

@Getter
@Entity
@Table(name = "reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "contract_id", nullable = false)
    private Integer contractId;

    @Column(name = "dev_score", nullable = false)
    private Integer devScore;

    @Column(name = "exp_score", nullable = false)
    private Integer expScore;

    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "reply", columnDefinition = "text")
    private String reply;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    @Column(name = "developer_id", nullable = false)
    private Integer developerId;
}
