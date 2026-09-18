import { describe, expect, it } from "vitest";
import {
  ADMIN_CONTRACT_TYPE_LABEL,
  ADMIN_PARTY_ROLE_LABEL,
  ADMIN_REPORT_STATUS_LABEL,
  contractTemplateStatusLabel,
  ESCROW_STATUS_LABEL,
  RECONCILIATION_RESULT_LABEL,
  reportPartyLabel,
} from "./labels";

describe("운영 콘솔 상태 문구", () => {
  it("운영자에게 보이는 문구를 고정한다", () => {
    expect(ADMIN_REPORT_STATUS_LABEL.OPEN).toBe("접수됨");
    expect(ADMIN_REPORT_STATUS_LABEL.RESOLVED).toBe("종결됨");
    expect(ADMIN_PARTY_ROLE_LABEL.DEVELOPER).toBe("개발자");
    expect(ADMIN_CONTRACT_TYPE_LABEL.CONSULTING).toBe("컨설팅");
    expect(ESCROW_STATUS_LABEL.REFUNDED).toBe("환불 처리 중");
    expect(contractTemplateStatusLabel(true)).toBe("사용 중");
    expect(contractTemplateStatusLabel(false)).toBe("보관");
  });

  it("대사 결과를 일치·불일치 둘로 뭉개지 않는다", () => {
    expect(RECONCILIATION_RESULT_LABEL.MATCHED).toBe("일치");
    expect(RECONCILIATION_RESULT_LABEL.BANK_MISMATCH).toBe("차이 발생");
    expect(RECONCILIATION_RESULT_LABEL.ERROR).toBe("조회 오류");
    expect(new Set(Object.values(RECONCILIATION_RESULT_LABEL)).size).toBe(
      Object.keys(RECONCILIATION_RESULT_LABEL).length,
    );
  });

  it("신고자 칸을 이름과 역할 한 덩어리로 적는다", () => {
    expect(reportPartyLabel({ name: "김미미", role: "CLIENT" })).toBe("김미미 (의뢰인)");
  });
});
