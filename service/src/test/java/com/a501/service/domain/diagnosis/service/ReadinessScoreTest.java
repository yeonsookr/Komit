package com.a501.service.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.domain.diagnosis.dto.request.ScanResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 런칭 준비도 계산. 결과 파일에 담겨 온 점수를 쓰지 않고 서버가 정하므로, 감점표를 여기서 못 박는다. (R-SCAN-10 · R-SCAN-18)
 */
class ReadinessScoreTest {
    @Test
    @DisplayName("결함이 없으면 100이다")
    void noIssuesIsFullScore() {
        assertThat(ReadinessScore.of(List.of())).isEqualTo(100);
    }

    @Test
    @DisplayName("낮음만 있으면 100에서 건당 2씩 깎고 12까지만 깎는다")
    void lowOnlyStartsAtHundred() {
        assertThat(ReadinessScore.of(issues(0, 0, 1))).isEqualTo(98);
        assertThat(ReadinessScore.of(issues(0, 0, 6))).isEqualTo(88);
        assertThat(ReadinessScore.of(issues(0, 0, 50))).isEqualTo(88);
    }

    @Test
    @DisplayName("보통이 있으면 79에서 시작해 보통은 건당 2, 낮음은 건당 1씩 깎는다")
    void mediumStartsAtSeventyNine() {
        assertThat(ReadinessScore.of(issues(0, 1, 0))).isEqualTo(77);
        assertThat(ReadinessScore.of(issues(0, 1, 2))).isEqualTo(75);
        assertThat(ReadinessScore.of(issues(0, 30, 30))).isEqualTo(53);
    }

    @Test
    @DisplayName("높음이 하나라도 있으면 49에서 시작해 높음은 건당 3, 보통은 건당 1씩 깎는다")
    void highStartsAtFortyNine() {
        assertThat(ReadinessScore.of(issues(1, 0, 0))).isEqualTo(46);
        assertThat(ReadinessScore.of(issues(1, 2, 0))).isEqualTo(44);
        assertThat(ReadinessScore.of(issues(1, 0, 99))).isEqualTo(46);
    }

    @Test
    @DisplayName("낮음은 높음 구간에서 점수를 깎지 않는다")
    void lowDoesNotCountInHighBand() {
        assertThat(ReadinessScore.of(issues(2, 1, 10)))
                .isEqualTo(ReadinessScore.of(issues(2, 1, 0)));
    }

    @Test
    @DisplayName("예시 프로젝트의 결함 구성은 11점이다")
    void soapShopScoresEleven() {
        assertThat(ReadinessScore.of(issues(16, 17, 1))).isEqualTo(11);
    }

    private static List<ScanResult.Issue> issues(int high, int medium, int low) {
        List<ScanResult.Issue> issues = new ArrayList<>();
        add(issues, "HIGH", high);
        add(issues, "MEDIUM", medium);
        add(issues, "LOW", low);
        return issues;
    }

    private static void add(List<ScanResult.Issue> issues, String severity, int count) {
        for (int index = 0; index < count; index++) {
            issues.add(new ScanResult.Issue(
                    "제목", null, "app/page.tsx:1", "SECURITY", severity, false, "fp-" + severity + index));
        }
    }
}
