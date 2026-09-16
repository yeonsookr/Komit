import { ROUTES } from "./routes";

export interface PolicyDocument {
  /** 화면에 찍는 문서 이름. */
  label: string;
  /** 개정일. 화면에 어떻게 찍을지는 `formatFullDate` 가 정합니다. */
  revisedAt: string;
  /** 본문 주소. */
  href: string;
}

/**
 * 가입할 때 동의한 문서 네 건.
 *
 * 서버가 주는 값이 아니라 화면이 가진 정적 목록입니다. 목 명세 영역 8 에 이 목록을
 * 주는 API 가 없습니다. 개정일도 여기서 관리합니다.
 *
 * 본문은 아직 없습니다. 이용약관·개인정보 처리방침은 법적 효력이 있는 문서라 실제
 * 문구가 확정된 뒤에 페이지를 만듭니다. 그때까지 「보기」는 404 입니다.
 */
export const POLICY_DOCUMENTS: PolicyDocument[] = [
  { label: "이용약관", revisedAt: "2026-03-01", href: ROUTES.policiesTerms },
  { label: "개인정보 처리방침", revisedAt: "2026-06-15", href: ROUTES.policiesPrivacy },
  { label: "전자금융거래 이용약관", revisedAt: "2026-03-01", href: ROUTES.policiesElectronicFinance },
  { label: "환불과 분쟁 처리 기준", revisedAt: "2026-03-01", href: ROUTES.policiesRefundDispute },
];
