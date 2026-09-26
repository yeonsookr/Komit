package com.a501.service.domain.review;

import com.a501.service.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 리뷰 도메인 실패 코드. 상태·코드·문구는 목 명세({@code /api/reviews/{reviewId}/reply})의 설명을 그대로 옮겼습니다. */
public enum ReviewErrorCode implements ErrorCode {
    /** {@code reviewId}의 리뷰가 없거나 삭제되었을 때. 목은 {@code reviewId}=404로 재현합니다. */
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다."),
    /** 거래를 마치지 않은 계약에 리뷰를 쓰려 할 때. (R-REV-02) */
    CONTRACT_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "CONTRACT_NOT_COMPLETED", "계약이 끝나야 리뷰를 남길 수 있습니다."),
    /** 이미 리뷰를 남긴 계약일 때. 계약 하나에 리뷰는 하나입니다. (R-REV-02) */
    REVIEW_ALREADY_WRITTEN(HttpStatus.CONFLICT, "REVIEW_ALREADY_WRITTEN", "이미 리뷰를 남기셨습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ReviewErrorCode(HttpStatus status, String code, String message) {
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
