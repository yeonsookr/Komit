package com.a501.escrow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 잔액·정산 합계·대사 합계를 어느 기록에서 읽을지 정합니다. (R-PAY-15)
 *
 * @param readSource TRANSACTIONS 는 완료 거래 합산, LEDGER 는 복식부기 분개 합산입니다.
 *                   두 값은 운영 화면의 원장 대조가 일치하는 동안 같습니다. 기본값은 TRANSACTIONS 입니다.
 */
@ConfigurationProperties(prefix = "app.ledger")
public record LedgerProperties(@DefaultValue("TRANSACTIONS") ReadSource readSource) {

    public enum ReadSource { TRANSACTIONS, LEDGER }

    public boolean readsLedger() {
        return readSource == ReadSource.LEDGER;
    }
}
