package com.a501.service.domain.account.entity;

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
@Table(name = "accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends SoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "account_holder", nullable = false, length = 50)
    private String accountHolder;

    @Column(name = "masked_number", nullable = false, length = 30)
    private String maskedNumber;

    @Column(name = "bank_code_id", nullable = false)
    private Integer bankCodeId;

    @Column(name = "account_no_encrypted", length = 255)
    private String accountNoEncrypted;

    public static Account register(Integer userId, String accountHolder, String maskedNumber,
            Integer bankCodeId, String accountNoEncrypted) {
        Account account = new Account();
        account.userId = userId;
        account.replace(accountHolder, maskedNumber, bankCodeId, accountNoEncrypted);
        return account;
    }

    public void replace(String accountHolder, String maskedNumber, Integer bankCodeId,
            String accountNoEncrypted) {
        this.accountHolder = accountHolder;
        this.maskedNumber = maskedNumber;
        this.bankCodeId = bankCodeId;
        this.accountNoEncrypted = accountNoEncrypted;
    }

    /**
     * 탈퇴한 사람의 명의와 계좌번호를 지웁니다. 명의는 비울 수 없어 문구로 바꾸고, 가린 번호는 화면 표시용이라 남깁니다. (R-ACC-06, R-LEG-02)
     * 행은 남겨 입출금 기록이 계속 가리키게 합니다.
     */
    public void anonymize(String withdrawnName) {
        this.accountHolder = withdrawnName;
        this.accountNoEncrypted = null;
    }
}
