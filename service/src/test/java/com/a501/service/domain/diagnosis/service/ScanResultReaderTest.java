package com.a501.service.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.SurveyAnswerType;
import com.a501.service.domain.diagnosis.dto.request.ScanResult;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * 올라온 결과 파일을 읽고 받을지 가린다. 읽지 못한 파일을 고쳐 읽으려 다시 시도하지 않는다.
 */
class ScanResultReaderTest {
    private static final int DIAGNOSIS_ID = 31;

    private final ScanResultReader reader = new ScanResultReader(JsonMapper.builder().build());

    @Test
    @DisplayName("마크다운 끝의 json 블록을 읽는다")
    void readsJsonBlockFromMarkdown() {
        ScanResult result = reader.read(file("komit-result.md", """
                # 검사 결과

                사람이 읽는 요약입니다.

                ```json
                {"diagnosisId":31,"scanVersion":"0.1.0","scannedAt":"2026-08-17T09:12:00+09:00",
                 "techCategories":["Next.js"],
                 "issues":[{"title":"관리자 API에 인증이 없습니다","location":"app/api/admin/route.ts:3",
                            "category":"SECURITY","severity":"HIGH","isInput":false,"fingerprint":"fp-1"}],
                 "questions":[{"content":"실제 결제를 받나요?","answerType":"SINGLE","options":["네","아니요"]}]}
                ```
                """), DIAGNOSIS_ID);

        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().getFirst().title()).isEqualTo("관리자 API에 인증이 없습니다");
        assertThat(result.techCategories()).containsExactly("Next.js");
        assertThat(result.questions().getFirst().answerType()).isEqualTo(SurveyAnswerType.SINGLE);
    }

    @Test
    @DisplayName("블록이 여럿이면 마지막 것을 쓴다. Komit 이 읽는 블록은 파일 끝에 있다")
    void usesLastJsonBlock() {
        ScanResult result = reader.read(file("komit-result.md", """
                ```json
                {"scanVersion":"0.0.1","issues":[]}
                ```

                ```json
                {"scanVersion":"0.1.0","issues":[],"techCategories":[]}
                ```
                """), DIAGNOSIS_ID);

        assertThat(result.scanVersion()).isEqualTo("0.1.0");
    }

    @Test
    @DisplayName("블록이 없으면 파일 전체를 JSON 으로 본다")
    void acceptsBareJsonFile() {
        ScanResult result = reader.read(
                file("komit-result.json", "{\"scanVersion\":\"0.1.0\",\"issues\":[]}"), DIAGNOSIS_ID);

        assertThat(result.issues()).isEmpty();
    }

    @Test
    @DisplayName("받는 형식이 아닌 확장자는 읽지 않는다")
    void rejectsOtherExtensions() {
        assertThatThrownBy(() -> reader.read(
                        file("komit-result.txt", "{\"scanVersion\":\"0.1.0\",\"issues\":[]}"), DIAGNOSIS_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(DiagnosisErrorCode.SCAN_FILE_INVALID);
    }

    @Test
    @DisplayName("JSON 이 깨졌거나 결함 목록이 없으면 받지 않는다")
    void rejectsUnreadableContent() {
        assertThatThrownBy(() -> reader.read(file("komit-result.md", "그냥 글입니다"), DIAGNOSIS_ID))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reader.read(
                        file("komit-result.md", "{\"scanVersion\":\"0.1.0\"}"), DIAGNOSIS_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(DiagnosisErrorCode.SCAN_FILE_INVALID);
    }

    @Test
    @DisplayName("다른 진단의 결과 파일은 받지 않는다")
    void rejectsOtherDiagnosis() {
        assertThatThrownBy(() -> reader.read(
                        file("komit-result.md", "{\"diagnosisId\":99,\"scanVersion\":\"0.1.0\",\"issues\":[]}"),
                        DIAGNOSIS_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(DiagnosisErrorCode.DIAGNOSIS_MISMATCH);
    }

    @Test
    @DisplayName("진단 번호가 적혀 있지 않으면 대조하지 않는다")
    void acceptsFileWithoutDiagnosisId() {
        ScanResult result = reader.read(
                file("komit-result.md", "{\"scanVersion\":\"0.1.0\",\"issues\":[]}"), DIAGNOSIS_ID);

        assertThat(result.diagnosisId()).isNull();
    }

    @Test
    @DisplayName("지난 판 지시문으로 만든 결과는 받지 않는다")
    void rejectsOutdatedScanVersion() {
        assertThatThrownBy(() -> reader.read(
                        file("komit-result.md", "{\"scanVersion\":\"0.0.9\",\"issues\":[]}"), DIAGNOSIS_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(DiagnosisErrorCode.SCAN_VERSION_OUTDATED);
    }

    private static MockMultipartFile file(String name, String body) {
        return new MockMultipartFile("file", name, "text/markdown", body.getBytes(StandardCharsets.UTF_8));
    }
}
