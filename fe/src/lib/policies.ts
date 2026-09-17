import { ROUTES } from "./routes";

export interface PolicyDocument {
  /** 화면에 찍는 문서 이름. */
  label: string;
  /** 개정일. 화면에 어떻게 찍을지는 `formatFullDate` 가 정합니다. */
  revisedAt: string;
  /** 본문 주소. */
  href: string;
  /** 참이면 개발자에게만 보여줍니다. 없으면 모두에게 보여줍니다. */
  developerOnly?: boolean;
}

/**
 * 동의한 문서 목록.
 *
 * 서버가 주는 값이 아니라 화면이 가진 정적 목록입니다. 목 명세 영역 8 에 이 목록을
 * 주는 API 가 없습니다. 개정일도 여기서 관리합니다.
 *
 * 앞의 넷은 필수라 로그인할 때 함께 동의한 것으로 봅니다. 판매자 약관만 선택이고
 * 개발자 프로필을 만드는 것으로 동의를 갈음하므로, 프로필이 있는 사람에게만
 * 보여줍니다.
 *
 * 본문은 아직 없습니다. 법적 효력이 있는 문서라 실제 문구가 확정된 뒤에 페이지를
 * 만듭니다. 그때까지 「보기」는 404 입니다.
 */
export const POLICY_DOCUMENTS: PolicyDocument[] = [
  { label: "이용약관", revisedAt: "2026-03-01", href: ROUTES.policiesTerms },
  { label: "개인정보 처리방침", revisedAt: "2026-06-15", href: ROUTES.policiesPrivacy },
  { label: "전자금융거래 이용약관", revisedAt: "2026-03-01", href: ROUTES.policiesElectronicFinance },
  { label: "환불과 분쟁 처리 기준", revisedAt: "2026-03-01", href: ROUTES.policiesRefundDispute },
  {
    label: "개발자 판매자 약관",
    revisedAt: "2026-03-01",
    href: ROUTES.policiesSellerTerms,
    developerOnly: true,
  },
];

/** 이 사람에게 보여줄 문서만 거릅니다. */
export function visibleDocuments(isDeveloper: boolean): PolicyDocument[] {
  return POLICY_DOCUMENTS.filter((doc) => !doc.developerOnly || isDeveloper);
}
