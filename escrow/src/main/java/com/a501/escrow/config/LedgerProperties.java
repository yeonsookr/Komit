package com.a501.escrow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 잔액·정산 합계·대사 합계를 어느 기록에서 읽을지 정합니다. (R-PAY-15)
 *
 * @param readSource LEDGER 는 복식부기 분개 합산, TRANSACTIONS 는 완료 거래 합산입니다.
 *                   두 값은 운영 화면의 원장 대조가 일치하는 동안 같습니다. 기본값은 LEDGER 이고,
 *                   TRANSACTIONS 는 원장에 문제가 생겼을 때 되돌리는 용도입니다.
 */
@ConfigurationProperties(prefix = "app.ledger")
public record LedgerProperties(@DefaultValue("LEDGER") ReadSource readSource) {

    public enum ReadSource { TRANSACTIONS, LEDGER }

    public boolean readsLedger() {
        return readSource == ReadSource.LEDGER;
    }
}
