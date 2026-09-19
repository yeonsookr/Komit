package com.a501.escrow.common.security;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.exception.EscrowApiException;
import com.a501.escrow.config.AppProperties;
import com.a501.escrow.config.InstanceRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WriterGuard {

    private final AppProperties properties;

    public WriterGuard(AppProperties properties) {
        this.properties = properties;
    }

    public void requireWriter(EscrowRequestHeader header) {
        if (properties.instanceRole() != InstanceRole.WRITER) {
            throw new EscrowApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Q1000",
                    "서비스를 일시적으로 사용할 수 없습니다.",
                    header);
        }
    }
}
