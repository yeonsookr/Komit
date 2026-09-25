package com.a501.service.domain.contract;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ContractErrorCode implements ErrorCode {
    /** 없는 계약이거나 그 계약의 당사자가 아니다. 존재를 알리지 않으려 둘을 같게 다룬다. */
    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "계약서를 찾을 수 없습니다."),
    /** 무료 상담에서 컨설팅·개발 갈래로 정상 전환된 방이 아니다. */
    ROOM_NOT_CONVERTED(
            HttpStatus.CONFLICT, "ROOM_NOT_CONVERTED", "갈래를 정하기 전에는 계약 초안을 만들 수 없습니다."),
    /** 한쪽이라도 서명한 뒤에는 고칠 수 없다. 그때는 채팅으로 이야기한 뒤 다시 쓴다. (`R-CON-02`) */
    CONTRACT_ALREADY_SIGNED(
            HttpStatus.CONFLICT, "CONTRACT_ALREADY_SIGNED", "이미 서명한 계약서는 고칠 수 없습니다."),
    /** 같은 사람이 두 번 서명할 수는 없다. 상대의 서명을 기다리는 중이다. */
    ALREADY_SIGNED_BY_ME(HttpStatus.CONFLICT, "ALREADY_SIGNED_BY_ME", "이미 서명하셨습니다."),
    /** 한 계약에 예치는 한 번이다. 멱등키는 같은 요청의 재시도를 가릴 뿐이다. */
    ALREADY_DEPOSITED(HttpStatus.CONFLICT, "ALREADY_DEPOSITED", "이미 예치했습니다."),
    /** 의뢰인이 취소한 계약이다. 서명·수정·예치를 받지 않는다. (`R-CON-21`) */
    CONTRACT_CANCELLED(HttpStatus.CONFLICT, "CONTRACT_CANCELLED", "취소된 계약입니다."),
    /** 양쪽이 서명해 성립한 계약은 취소로 되돌리지 않는다. (`R-CON-21`) */
    CONTRACT_CONCLUDED(HttpStatus.CONFLICT, "CONTRACT_CONCLUDED", "양쪽이 서명해 성립한 계약은 취소할 수 없습니다."),
    /** 계약은 취소했지만 환불 결과를 아직 모른다. 같은 요청을 다시 보내면 같은 멱등키로 잇는다. */
    REFUND_PENDING(
            HttpStatus.SERVICE_UNAVAILABLE,
            "REFUND_PENDING",
            "계약을 취소했습니다. 환불을 확인하는 중이니 잠시 뒤 다시 눌러 주세요."),
    /** 의뢰인이 계약서에 서명하기 전에는 돈을 맡기지 않는다. (`R-CON-20`) */
    CONTRACT_NOT_SIGNED(HttpStatus.CONFLICT, "CONTRACT_NOT_SIGNED", "계약서에 서명한 뒤에 예치할 수 있습니다."),
    /** Escrow 서버에 닿지 못했다. 돈이 오갔는지 단정하지 않는다. */
    ESCROW_UNAVAILABLE(
            HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "예치를 처리하지 못했습니다.");

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
