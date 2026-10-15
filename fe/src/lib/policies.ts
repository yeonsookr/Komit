import { ROUTES } from "./routes";

export interface PolicyDocument {
  /** 화면에 찍는 문서 이름. */
  label: string;
  /** 초안 작성일. 화면에 어떻게 찍을지는 `formatFullDate` 가 정합니다. */
  revisedAt: string;
  /** 본문 주소. */
  href: string;
  /** 참이면 개발자에게만 보여줍니다. 없으면 모두에게 보여줍니다. */
  developerOnly?: boolean;
}

/**
 * 약관 및 정책 문서 목록.
 *
 * 서버가 주는 값이 아니라 화면이 가진 정적 목록입니다. 목 명세 영역 8 에 이 목록을
 * 주는 API 가 없습니다. 초안 작성일도 여기서 관리합니다.
 *
 * 가입 필수 동의 항목은 R-ACC-12의 네 문서와 일치합니다.
 * 이 목록은 개인별 동의 이력을 증명하지 않습니다.
 */
export const POLICY_DOCUMENTS: PolicyDocument[] = [
  { label: "서비스 이용약관", revisedAt: "2026-09-20", href: ROUTES.policiesTerms },
  { label: "개인정보 수집 및 이용", revisedAt: "2026-09-20", href: ROUTES.policiesPrivacyCollection },
  { label: "개인정보 제3자 제공", revisedAt: "2026-09-20", href: ROUTES.policiesPrivacyThirdParty },
  { label: "에스크로 서비스 이용약관", revisedAt: "2026-09-20", href: ROUTES.policiesEscrow },
  { label: "개인정보 처리 안내", revisedAt: "2026-09-20", href: ROUTES.policiesPrivacy },
  { label: "전자금융거래 안내", revisedAt: "2026-09-20", href: ROUTES.policiesElectronicFinance },
  { label: "환불과 분쟁 처리 기준", revisedAt: "2026-09-20", href: ROUTES.policiesRefundDispute },
  {
    label: "개발자 판매자 약관",
    revisedAt: "2026-09-20",
    href: ROUTES.policiesSellerTerms,
    developerOnly: true,
  },
];

/** 이 사람에게 보여줄 문서만 거릅니다. */
export function visibleDocuments(isDeveloper: boolean): PolicyDocument[] {
  return POLICY_DOCUMENTS.filter((doc) => !doc.developerOnly || isDeveloper);
}
