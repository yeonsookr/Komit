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
            "검사 결과 파일을 읽지 못했습니다. 받은 파일 그대로 올려주세요."),
    /** 다른 진단에서 받은 검사 파일로 검사한 결과다. 진단끼리 결과가 섞이지 않도록 받지 않는다. (R-SCAN-03) */
    DIAGNOSIS_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "DIAGNOSIS_MISMATCH",
            "다른 진단의 결과 파일입니다. 이 진단에서 받은 검사 파일로 다시 검사해 주세요."),
    /** 지난 판 검사 지시문으로 만든 결과다. 판이 다르면 담긴 내용도 다르므로 다시 받아 검사하게 한다. (R-SCAN-04) */
    SCAN_VERSION_OUTDATED(
            HttpStatus.BAD_REQUEST,
            "SCAN_VERSION_OUTDATED",
            "검사 방법이 업데이트되었습니다. 새 검사 파일을 내려받아 다시 검사해 주세요."),
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
