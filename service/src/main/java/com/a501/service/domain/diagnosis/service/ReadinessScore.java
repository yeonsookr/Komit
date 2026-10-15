package com.a501.service.domain.diagnosis.service;

import com.a501.service.domain.diagnosis.dto.request.ScanResult;
import java.util.List;

/**
 * 런칭 준비도. 가장 위험한 결함이 구간을 정하고, 그 구간 안에서 건수만큼 깎는다. (R-SCAN-10 · R-SCAN-18)
 *
 * <p>결과 파일에도 점수가 담겨 오지만 쓰지 않는다. 같은 결함 목록이면 언제 계산해도 같은 값이어야 하고, 그 판단을 서버가 쥔다.
 */
final class ReadinessScore {
    private static final String HIGH = "HIGH";
    private static final String MEDIUM = "MEDIUM";
    private static final String LOW = "LOW";

    private ReadinessScore() {
    }

    static int of(List<ScanResult.Issue> issues) {
        long high = count(issues, HIGH);
        long medium = count(issues, MEDIUM);
        long low = count(issues, LOW);

        if (high > 0) {
            return (int) (49 - Math.min(high * 3, 30) - Math.min(medium, 8));
        }
        if (medium > 0) {
            return (int) (79 - Math.min(medium * 2, 20) - Math.min(low, 6));
        }
        if (low > 0) {
            return (int) (100 - Math.min(low * 2, 12));
        }
        return 100;
    }

    private static long count(List<ScanResult.Issue> issues, String severity) {
        return issues.stream().filter(issue -> severity.equals(issue.severity())).count();
    }
}
