/**
 * 화면 경로 상수. 컴포넌트가 URL 문자열을 직접 조립하지 않습니다.
 * (docs/FRONTEND_CONVENTIONS.md 8.1)
 *
 * API 경로는 `lib/api/endpoints.ts`, 화면 경로는 여기입니다.
 * 목적지가 바뀌면 이 파일만 고칩니다. 아직 화면이 없는 목적지는 주석으로 표시합니다.
 *
 * query 이름은 여기서 정하지 않고 그 값을 읽는 모듈에서 가져옵니다 (컨벤션 8.2).
 */
import { ERROR_PARAM, NEXT_PARAM } from "./auth/params";
import { FIELD_CODE_IDS_PARAM } from "./developerSearch";
import { STEP_PARAM } from "./survey";

function signupPath(path: string, next?: string): string {
  return next ? `${path}?${NEXT_PARAM}=${encodeURIComponent(next)}` : path;
}

export const ROUTES = {
  home: "/",
  /** 이용 방법 화면은 따로 없습니다. 랜딩의 진행 순서 섹션이 그 역할입니다. */
  howTo: "/#process",
  login: "/login",
  /** 로그인에 실패해 돌아올 때. `code` 는 서버의 실패 코드(KAKAO_AUTH_FAILED 등)입니다. */
  loginFailed: (code: string) => `/login?${ERROR_PARAM}=${encodeURIComponent(code)}`,
  /** 카카오 인가 시작. Route Handler(`app/api/auth/kakao`)가 카카오로 보냅니다. */
  kakaoStart: "/api/auth/kakao",
  signupTerms: (next?: string) => signupPath("/signup/terms", next),
  signupAccount: (next?: string) => signupPath("/signup/account", next),
  signupIdentity: (next?: string) => signupPath("/signup/identity", next),
  signupVerification: (next?: string) => signupPath("/signup/verification", next),
  signupComplete: (next?: string) => signupPath("/signup/complete", next),

  developers: "/developers",
  developersByField: (codeId: number) =>
    `/developers?${FIELD_CODE_IDS_PARAM}=${codeId}`,
  developer: (developerId: number) => `/developers/${developerId}`,
  /** 무료로 물어보기 — 어느 요청서로 물어볼지 고릅니다. 로그인해야 들어옵니다. */
  developerInquiry: (developerId: number) => `/developers/${developerId}/inquiry`,

  /**
   * 약관과 정책. 문서마다 주소가 따로입니다.
   * 개인정보 처리방침처럼 주소 하나만 따로 제출해야 하는 문서가 있어 한 페이지에
   * 모으지 않습니다. 목록은 마이페이지 약관 칸이 맡고 별도 목록 화면은 두지 않습니다.
   * 문서 목록과 초안 작성일은 `lib/policies.ts` 에 있습니다.
   */
  policiesTerms: "/policies/terms",
  policiesPrivacy: "/policies/privacy",
  policiesPrivacyCollection: "/policies/privacy-collection",
  policiesPrivacyThirdParty: "/policies/privacy-third-party",
  policiesEscrow: "/policies/escrow",
  policiesElectronicFinance: "/policies/electronic-finance",
  policiesRefundDispute: "/policies/refund-dispute",
  /** 개발자 판매자 약관. 개발자 프로필을 만들면 동의한 것으로 봅니다. */
  policiesSellerTerms: "/policies/seller-terms",

  /** 사전점검(진단) 시작. 로그인이 필요합니다. */
  checkup: "/checkup",
  /** 설문 2~4단계. 1단계는 진단이 없으므로 `checkup` 입니다. */
  checkupSurvey: (diagnosisId: number, step: number) =>
    `/checkup/${diagnosisId}?${STEP_PARAM}=${step}`,
  /** 진단서 — 발견된 문제점. 설문 「제출하기」가 여기로 옵니다. */
  checkupResult: (diagnosisId: number) => `/checkup/${diagnosisId}/result`,
  /** 요구사항 선택. 진단서 「요청서 만들기」가 여기로 옵니다. */
  checkupRequirements: (diagnosisId: number) => `/checkup/${diagnosisId}/requirements`,
  /**
   * 요청서. 고른 요구사항을 확인하고 개발자 찾기로 넘어갑니다.
   * 요청서는 따로 저장되는 문서가 아니라 요구사항들을 모아 부르는 이름입니다.
   */
  checkupRequest: (diagnosisId: number) => `/checkup/${diagnosisId}/request`,
  /** 방에서 여는 요청서. 읽기만 하며, 고치는 일은 의뢰인이 사전점검 화면에서 합니다. (`R-REQ-05`) */
  roomRequest: (roomId: number) => `/rooms/${roomId}/request`,
  /** 요청서 수정. 요청서의 「요청서 수정하기」가 여기로 옵니다. */
  checkupRequestEdit: (diagnosisId: number) => `/checkup/${diagnosisId}/request/edit`,

  /** 방 대화. 상담·컨설팅·개발 방이 같은 주소를 씁니다. 방의 기본 탭입니다. */
  room: (roomId: number) => `/rooms/${roomId}`,
  /**
   * 방 안의 탭. 의뢰인과 개발자가 같은 주소를 쓰고 화면이 역할로 갈립니다
   * — 방 응답의 `developerId` 와 내 `developerId` 를 견줍니다.
   */
  roomContract: (roomId: number) => `/rooms/${roomId}/contract`,
  /** 개발자가 계약서를 처음 쓰는 화면. */
  roomContractNew: (roomId: number) => `/rooms/${roomId}/contract/new`,
  /** 개발자가 계약서를 고치는 화면. 서명 전에만 들어갑니다 (R-CON-02). */
  roomContractEdit: (roomId: number) => `/rooms/${roomId}/contract/edit`,
  /** 의뢰인이 서명하고 계약 금액 전액을 맡기는 화면. */
  roomDeposit: (roomId: number) => `/rooms/${roomId}/deposit`,
  roomProgress: (roomId: number) => `/rooms/${roomId}/progress`,
  roomCheck: (roomId: number) => `/rooms/${roomId}/check`,
  /** 컨설팅 방의 요약 탭. 세션이 끝나야 내용이 생깁니다 (`4_의뢰인_요약`). */
  roomSummary: (roomId: number) => `/rooms/${roomId}/summary`,
  /**
   * 세션 화면 (`4_의뢰인_세션중`).
   *
   * **방 안의 탭이 아니라 별도 화면입니다.** 화상과 화면 공유가 화면을 가득 채워
   * 사이드바도 방 머리도 두지 않습니다(`docs/FRONTEND_COMPONENTS.md` 2장 — 골격이
   * 다른 화면은 별도 레이아웃). 그래서 방이 아니라 세션 식별자로 찾아갑니다.
   */
  session: (sessionId: number) => `/sessions/${sessionId}`,
  /** 계약이 끝난 뒤에만 들어갑니다 (R-REV-02). */
  roomReview: (roomId: number) => `/rooms/${roomId}/review`,
  /** 문제 신고. 의뢰인·개발자 모두 같은 자리에서 넣습니다. (화면 없음 — 영역 6) */
  roomReport: (roomId: number) => `/rooms/${roomId}/report`,
  /** 개발자 추천 결과. 요구사항을 고르면 만들어지는 회차입니다. */
  recommendation: (batchId: number) => `/recommendations/${batchId}`,

  /** 마이페이지. 계정·계좌·프로젝트·개발자 프로필·알림 설정을 한 화면에서 봅니다. */
  mypage: "/mypage",
  /** 회원 탈퇴. 마이페이지의 「탈퇴하기」가 옵니다. */
  mypageWithdrawal: "/mypage/withdrawal",

  /**
   * 의뢰 목록. 헤더 「의뢰」가 여기로 옵니다.
   *
   * 방 화면(`room`)과 같은 세그먼트를 씁니다 — 목록이 `/rooms`, 방 하나가
   * `/rooms/{roomId}` 입니다. 목록에서는 어느 방도 고르지 않은 채 왼쪽 목록만
   * 보여줍니다.
   */
  requests: "/rooms",

  /**
   * 정산. 한 사람이 의뢰인이면서 개발자일 수 있어 소비와 소득을 함께 둡니다.
   * 기본 화면은 소비 내역이고, 달은 `?month=` 로 넘깁니다(`lib/settlement.ts`).
   */
  settlements: "/settlements",
  settlementsIncome: "/settlements/income",
  settlementsDeposits: "/settlements/deposits",
  notifications: "/notifications",
  /** 계좌 등록·변경. 마이페이지 계좌 칸과 예치 화면이 옵니다. */
  accountRegister: "/mypage/account",
  /** 내 개발자 프로필. 만들기와 편집이 같은 화면을 씁니다. */
  mypageProfile: "/mypage/profile",
  /** 내 일정. 마이페이지의 「일정 관리」가 옵니다. */
  mypageSchedule: "/mypage/schedule",

  /** 내부 운영 콘솔. 사용자 세션과 별도의 운영자 세션을 사용합니다. */
  adminLogin: "/admin/login",
  adminHome: "/admin",
  adminReports: "/admin/reports",
  adminReconciliation: "/admin/reconciliation",
  adminContractTemplates: "/admin/contract-templates",
  adminContractTemplateNew: "/admin/contract-templates/new",
  adminContractTemplateEdit: (templateId: number) =>
    `/admin/contract-templates/${templateId}/edit`,

  // ---- 아래는 아직 화면이 없는 임시 경로입니다. 화면이 생기면 값만 고칩니다. ----
  /** 푸터 "정산 방식" 안내. (docs/FRONTEND_COMPONENTS.md 에 화면 없음으로 적혀 있습니다) */
  settlementsGuide: "/settlements/guide",
} as const;
