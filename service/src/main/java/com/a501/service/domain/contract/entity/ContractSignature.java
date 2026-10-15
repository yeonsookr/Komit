package com.a501.service.domain.contract.entity;

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

@Getter
@Entity
@Table(name = "contract_signatures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractSignature extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ColumnDefault("FALSE")
    @Column(name = "is_signed", nullable = false)
    private boolean signed = false;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "signature_image_file_id")
    private Integer signatureImageFileId;

    /**
     * 서명판에 그린 그림으로 남기는 서명. 그림은 먼저 올려 받은 파일 식별자로 가리킵니다.
     *
     * <p>서명한 시각을 함께 남깁니다. 언제 합의했는지가 분쟁에서 기준이 됩니다.
     */
    public static ContractSignature of(
            Integer userId, Integer signatureImageFileId, LocalDateTime signedAt) {
        ContractSignature signature = new ContractSignature();
        signature.userId = Objects.requireNonNull(userId, "userId");
        signature.signatureImageFileId =
                Objects.requireNonNull(signatureImageFileId, "signatureImageFileId");
        signature.signedAt = Objects.requireNonNull(signedAt, "signedAt");
        signature.signed = true;
        return signature;
    }
}
