package com.a501.service.domain.diagnosis.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.dto.request.ScanResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 올라온 검사 결과 파일을 읽는다.
 *
 * <p>읽을 수 없으면 받지 않는다. 조용히 넘기면 의뢰인은 올라간 줄 알고 예전 진단서를 보게 된다. 읽지 못한 파일을 고쳐 읽으려 다시 시도하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ScanResultReader {
    /** 결과 파일은 마크다운이고, Komit 이 읽는 부분은 그 안의 json 코드블록이다. 앞부분은 의뢰인이 읽는 요약이라 모양이 정해져 있지 않다. */
    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*\\n(.*?)\\n```", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public ScanResult read(MultipartFile file, Integer diagnosisId) {
        String name = file.getOriginalFilename();
        if (name == null || !(name.endsWith(".md") || name.endsWith(".json"))) {
            throw new BusinessException(DiagnosisErrorCode.SCAN_FILE_INVALID);
        }

        ScanResult result = parse(readText(file));
        if (result.issues() == null) {
            throw new BusinessException(DiagnosisErrorCode.SCAN_FILE_INVALID);
        }
        // 번호가 적혀 있을 때만 대조한다. 검사 파일을 받아 만든 결과에는 번호가 항상 들어 있어
        // 다른 진단의 파일은 그대로 걸린다. (R-SCAN-03)
        if (result.diagnosisId() != null && !result.diagnosisId().equals(diagnosisId)) {
            throw new BusinessException(DiagnosisErrorCode.DIAGNOSIS_MISMATCH);
        }
        if (!ScanFileWriter.SCAN_VERSION.equals(result.scanVersion())) {
            throw new BusinessException(DiagnosisErrorCode.SCAN_VERSION_OUTDATED);
        }
        return result;
    }

    private String readText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(DiagnosisErrorCode.SCAN_FILE_INVALID);
        }
    }

    /** 코드블록이 여럿이면 마지막 것을 쓴다. 앞부분 요약에 블록이 섞여 있어도 Komit 이 읽는 블록은 파일 끝에 있다. */
    private ScanResult parse(String text) {
        Matcher matcher = JSON_BLOCK.matcher(text);
        String candidate = null;
        while (matcher.find()) {
            candidate = matcher.group(1);
        }
        try {
            return objectMapper.readValue(candidate == null ? text : candidate, ScanResult.class);
        } catch (JacksonException exception) {
            throw new BusinessException(DiagnosisErrorCode.SCAN_FILE_INVALID);
        }
    }
}
