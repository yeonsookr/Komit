package com.a501.service.domain.contract;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ContractErrorCode implements ErrorCode {
    /** 없는 계약이거나 그 계약의 당사자가 아니다. 존재를 알리지 않으려 둘을 같게 다룬다. */
    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "계약서를 찾을 수 없습니다."),
    /** 무료 상담에서 컨설팅·개발 갈래로 정상 전환된 방이 아니다. */
    ROOM_NOT_CONVERTED(
            HttpStatus.CONFLICT, "ROOM_NOT_CONVERTED", "갈래를 정하기 전에는 계약 초안을 만들 수 없습니다."),
    /**
     * 개발방이 아닌 곳에서 개발 계약서를 쓰려 했다. 화면에는 그런 길이 없어 API 를 직접 부를 때만 걸린다.
     * 부른 사람이 그 방의 당사자라 방이 없다고 감추지 않는다.
     */
    NOT_DEVELOPMENT_ROOM(
            HttpStatus.CONFLICT, "NOT_DEVELOPMENT_ROOM", "개발방에서만 개발 계약서를 쓸 수 있습니다."),
    /** 납기일이 착수일보다 이르다. 같은 날은 받는다. 형식 오류(422)가 아니라 400 이다. 목과 같다. */
    DUE_DATE_BEFORE_START(
            HttpStatus.BAD_REQUEST, "DUE_DATE_BEFORE_START", "납기일은 착수일보다 늦어야 합니다."),
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
    /** 양쪽이 서명해 계약이 성립하기 전에는 착수하지 않는다. (`R-CON-01`) */
    CONTRACT_NOT_CONCLUDED(
            HttpStatus.CONFLICT, "CONTRACT_NOT_CONCLUDED", "양쪽이 서명한 뒤에 착수할 수 있습니다."),
    /** 대금이 예치되기 전에는 착수하지 않는다. */
    DEPOSIT_REQUIRED(HttpStatus.CONFLICT, "DEPOSIT_REQUIRED", "예치가 끝나야 착수할 수 있습니다."),
    /** 착수는 한 번이다. 착수한 뒤로는 이 요청으로 되돌리지 않는다. */
    ALREADY_STARTED(HttpStatus.CONFLICT, "ALREADY_STARTED", "이미 착수한 계약입니다."),
    /** 없는 인계물이거나 그 계약의 당사자가 아니다. 존재를 알리지 않으려 둘을 같게 다룬다. */
    DELIVERABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "DELIVERABLE_NOT_FOUND", "인계물을 찾을 수 없습니다."),
    /** 양쪽이 서명하고 일반 작업을 모두 확정한 뒤에야 인계 자료를 올린다. (`R-QA-06`) */
    HANDOVER_NOT_OPEN(
            HttpStatus.CONFLICT, "HANDOVER_NOT_OPEN", "일반 작업을 모두 마친 뒤에 인계 자료를 등록할 수 있습니다."),
    /** 인계 작업을 확정한 뒤에는 검수한 자료와 받을 자료가 달라지지 않게 막는다. (`R-QA-06`) */
    FINAL_ACCEPTANCE_PASSED(
            HttpStatus.CONFLICT,
            "FINAL_ACCEPTANCE_PASSED",
            "최종 인수 시험을 통과한 뒤에는 인계 자료를 바꿀 수 없습니다."),
    /** 같은 계약에서 한 파일을 시험용과 해금용으로 함께 두면 시험용으로 지급 전에 받을 수 있다. (`R-QA-06`) */
    MATERIAL_STAGE_CONFLICT(
            HttpStatus.CONFLICT, "MATERIAL_STAGE_CONFLICT", "같은 파일을 시험용과 해금용으로 함께 등록할 수 없습니다."),
    /** 인계 작업의 확인을 요청한 뒤에는 의뢰인이 시험하는 자료가 바뀌지 않게 막는다. (`R-QA-06`) */
    HANDOVER_UNDER_REVIEW(
            HttpStatus.CONFLICT, "HANDOVER_UNDER_REVIEW", "최종 인수 시험 중에는 인계 자료를 바꿀 수 없습니다."),
    /** 인계 작업은 시험할 자료와 지급 뒤 받을 파일이 있어야 확인을 요청한다. (`R-QA-06`) */
    HANDOVER_MATERIALS_MISSING(
            HttpStatus.CONFLICT,
            "HANDOVER_MATERIALS_MISSING",
            "인수 시험 자료와 해금 파일을 등록한 뒤에 확인을 요청할 수 있습니다."),
    /** 지급 뒤 해금되는 파일은 대금 지급이 끝나기 전에는 의뢰인에게 주지 않는다. (`R-QA-06`) */
    DELIVERABLE_ACCESS_LOCKED(
            HttpStatus.CONFLICT, "DELIVERABLE_ACCESS_LOCKED", "대금 지급이 완료된 뒤에 내려받을 수 있습니다."),
    /** 기존 단일 파일 칸에 올라온 파일이 없다. */
    DELIVERABLE_NOT_UPLOADED(HttpStatus.CONFLICT, "DELIVERABLE_NOT_UPLOADED", "아직 올라온 파일이 없습니다."),
    /** 없는 작업이거나 그 계약의 당사자가 아니다. 존재를 알리지 않으려 둘을 같게 다룬다. */
    MILESTONE_NOT_FOUND(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "작업을 찾을 수 없습니다."),
    /** 착수 전이거나 이미 통과됐거나 확인 요청이 오기 전이다. */
    MILESTONE_STATE_CONFLICT(
            HttpStatus.CONFLICT, "MILESTONE_STATE_CONFLICT", "지금 상태에서는 처리할 수 없습니다."),
    /** 확인 문장을 모두 통과한 작업만 완료한다. (`R-QA-01`) */
    QA_NOT_ALL_CHECKED(
            HttpStatus.BAD_REQUEST, "QA_NOT_ALL_CHECKED", "확인 문장을 모두 체크해야 통과시킬 수 있습니다."),
    /** 의뢰인이 계약서에 서명하기 전에는 돈을 맡기지 않는다. (`R-CON-20`) */
    CONTRACT_NOT_SIGNED(HttpStatus.CONFLICT, "CONTRACT_NOT_SIGNED", "계약서에 서명한 뒤에 예치할 수 있습니다."),
    /** AI가 컨설팅 계약 초안 형식에 맞는 결과를 만들지 못했다. */
    AI_RESPONSE_INVALID(
            HttpStatus.BAD_GATEWAY,
            "AI_RESPONSE_INVALID",
            "계약 초안을 만들지 못했습니다. 잠시 후 다시 시도해 주세요."),
    /** AI Worker 연결 실패나 시간 초과로 결과를 받지 못했다. */
    AI_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AI_SERVICE_UNAVAILABLE",
            "계약 초안 생성 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
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
