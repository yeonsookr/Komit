package com.a501.service.domain.diagnosis.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 의뢰인이 자기 AI 도구에서 돌릴 검사 파일을 만든다.
 *
 * <p>검사 기준 본문은 공개된 검사 지시문(`ai/komit-scan`)에 있고, 이 파일은 그것을 어떻게 쓰는지와 <b>이 진단이 몇 번인지</b>를 담는다. 진단 번호를 검사 도구가 결과
 * 파일의 {@code diagnosisId} 로 옮겨 적고, 올릴 때 서버가 그것을 대조한다. (R-SCAN-03)
 */
@Component
public class ScanFileWriter {
    public static final String FILE_NAME = "komit-scan.md";
    /** 지시문 판. 결과 파일의 {@code scanVersion} 과 같아야 받는다. (R-SCAN-04) */
    public static final String SCAN_VERSION = "0.1.0";

    private static final String TEMPLATE_PATH = "scan/komit-scan.md";
    private static final String NO_USER_INPUT = "적지 않았습니다";

    private final String template;

    ScanFileWriter() {
        try {
            template = new ClassPathResource(TEMPLATE_PATH)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("검사 파일 서식을 읽지 못했습니다.", exception);
        }
    }

    public byte[] write(Integer diagnosisId, String projectName, String userInput) {
        if (diagnosisId == null || projectName == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        return template
                .replace("${diagnosisId}", diagnosisId.toString())
                .replace("${scanVersion}", SCAN_VERSION)
                .replace("${projectName}", projectName)
                .replace("${userInput}", userInput == null || userInput.isBlank() ? NO_USER_INPUT : userInput)
                .getBytes(StandardCharsets.UTF_8);
    }
}
