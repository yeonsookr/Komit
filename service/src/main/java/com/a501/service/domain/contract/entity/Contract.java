package com.a501.service.domain.contract.entity;

import com.a501.service.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "contracts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract extends SoftDeleteEntity {
    private static final String NEGOTIATING = "NEGOTIATING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "room_id", nullable = false)
    private Integer roomId;

    @Column(name = "contract_type", nullable = false, length = 50)
    private String contractType;

    @Column(name = "contract_type_group", nullable = false, length = 50)
    private String contractTypeGroup;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "status_group", nullable = false, length = 50)
    private String statusGroup;

    @Column(name = "contract_text", columnDefinition = "text")
    private String contractText;

    @Column(name = "template_id")
    private Integer templateId;

    @Column(name = "client_signature_id")
    private Integer clientSignatureId;

    @Column(name = "developer_signature_id")
    private Integer developerSignatureId;

    /**
     * 개발자가 써서 보낸 계약서. 아직 아무도 서명하지 않아 협의중으로 시작합니다.
     *
     * <p>체결 여부는 이 status 로만 봅니다. 컨설팅·개발 쪽 status 는 체결된 뒤의 진행을 담는
     * 값이라, 협의중인 동안에는 의미가 없습니다.
     */
    public static Contract of(Integer roomId, ContractType type) {
        Contract contract = new Contract();
        contract.roomId = Objects.requireNonNull(roomId, "roomId");
        contract.contractType = type.name();
        contract.contractTypeGroup = "CONTRACT_TYPE";
        contract.status = NEGOTIATING;
        contract.statusGroup = "CONTRACT_STATUS";
        return contract;
    }

    /** 한쪽이라도 서명했는지. 서명 뒤에는 고칠 수 없습니다. (`R-CON-02`) */
    public boolean isSigned() {
        return clientSignatureId != null || developerSignatureId != null;
    }

    public enum ContractType {
        CONSULTING,
        DEVELOPMENT
    }
}
