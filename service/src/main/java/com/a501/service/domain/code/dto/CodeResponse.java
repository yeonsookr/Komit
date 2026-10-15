package com.a501.service.domain.code.dto;

import com.a501.service.domain.code.entity.DynamicCode;

public record CodeResponse(Integer codeId, String codeName) {
    public static CodeResponse from(DynamicCode code) {
        return new CodeResponse(code.getCodeId(), code.getCodeName());
    }
}
