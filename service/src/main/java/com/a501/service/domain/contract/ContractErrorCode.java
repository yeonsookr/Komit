package com.a501.service.domain.contract;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ContractErrorCode implements ErrorCode {
    /** 없는 계약이거나 그 계약의 당사자가 아니다. 존재를 알리지 않으려 둘을 같게 다룬다. */
    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "계약서를 찾을 수 없습니다."),
    /** 한쪽이라도 서명한 뒤에는 고칠 수 없다. 그때는 채팅으로 이야기한 뒤 다시 쓴다. (`R-CON-02`) */
    CONTRACT_ALREADY_SIGNED(
            HttpStatus.CONFLICT, "CONTRACT_ALREADY_SIGNED", "이미 서명한 계약서는 고칠 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ContractErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
