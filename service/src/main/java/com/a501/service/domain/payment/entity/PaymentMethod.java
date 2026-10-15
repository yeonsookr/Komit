package com.a501.service.domain.payment.entity;

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
@Table(name = "payment_methods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethod extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "masked_number", length = 20)
    private String maskedNumber;

    @ColumnDefault("FALSE")
    @Column(name = "is_default", nullable = false)
    private boolean defaultMethod = false;

    @Column(name = "card_company_code_id")
    private Integer cardCompanyCodeId;

    @Column(name = "method_type", nullable = false, length = 50)
    private String methodType;

    @Column(name = "method_type_group", nullable = false, length = 50)
    private String methodTypeGroup;
}
