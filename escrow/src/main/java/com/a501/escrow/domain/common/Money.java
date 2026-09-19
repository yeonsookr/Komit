package com.a501.escrow.domain.common;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 원 단위 금액입니다. PostgreSQL NUMERIC(19, 0)의 양수 범위를 손실 없이 표현합니다.
 */
public record Money(BigInteger value) {

    public static final BigInteger MAX_VALUE = new BigInteger("9999999999999999999");

    public Money {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException("금액은 1 이상 10^19 미만이어야 합니다.");
        }
    }

    public static Money fromDatabase(BigDecimal value) {
        return new Money(value.toBigIntegerExact());
    }

    public BigDecimal toDatabase() {
        return new BigDecimal(value);
    }
}
