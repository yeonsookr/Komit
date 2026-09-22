package com.a501.service.domain.diagnosis;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum DiagnosisErrorCode implements ErrorCode {
    /** 없는 진단이거나 내 진단이 아니다. 존재를 알리지 않으려 둘을 같게 다룬다. */
    DIAGNOSIS_NOT_FOUND(
            HttpStatus.NOT_FOUND, "DIAGNOSIS_NOT_FOUND", "진단을 찾을 수 없습니다."),
    /** 없는 요구사항이거나 남의 요구사항이다. 진단과 같은 이유로 둘을 갈라 답하지 않는다. */
    ISSUE_NOT_FOUND(
            HttpStatus.NOT_FOUND, "ISSUE_NOT_FOUND", "요구사항을 찾을 수 없습니다."),
    /** 받은 형식이 아닌 파일을 올렸다. 내려받은 파일을 그대로 올리도록 안내한다. */
    SCAN_FILE_INVALID(
            HttpStatus.BAD_REQUEST,
            "SCAN_FILE_INVALID",
            "검사 결과 파일을 읽지 못했습니다. 받은 파일을 그대로 올려 주세요."),
    INSPECTION_ALREADY_RUNNING(
            HttpStatus.CONFLICT, "INSPECTION_ALREADY_RUNNING", "이미 검사가 진행 중입니다."),
    /** 답하지 않은 문항이 남았거나 이미 끝낸 설문이다. 둘 다 더 진행할 수 없다는 같은 뜻이라 한 코드로 답한다. */
    SURVEY_NOT_COMPLETABLE(
            HttpStatus.CONFLICT, "SURVEY_NOT_COMPLETABLE", "아직 끝내지 않은 문항이 있습니다."),
    /** 계약으로 넘어간 요구사항은 사전점검에서 더 건드리지 않는다. 빼는 것도 상태를 바꾸는 것도 막는다. */
    ISSUE_STATUS_CONFLICT(
            HttpStatus.CONFLICT,
            "ISSUE_STATUS_CONFLICT",
            "계약에 포함된 요구사항은 상태를 바꿀 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    DiagnosisErrorCode(HttpStatus status, String code, String message) {
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
