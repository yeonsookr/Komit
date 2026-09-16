/**
 * 화면 경로 상수. 컴포넌트가 URL 문자열을 직접 조립하지 않습니다.
 * (docs/FRONTEND_CONVENTIONS.md 8.1)
 *
 * API 경로는 `lib/api/endpoints.ts`, 화면 경로는 여기입니다.
 * 목적지가 바뀌면 이 파일만 고칩니다. 아직 화면이 없는 목적지는 주석으로 표시합니다.
 *
 * query 이름은 여기서 정하지 않고 그 값을 읽는 모듈에서 가져옵니다 (컨벤션 8.2).
 */
import { ERROR_PARAM } from "./auth/params";
import { FIELD_CODE_IDS_PARAM } from "./developerSearch";
import { STEP_PARAM } from "./survey";

export const ROUTES = {
  home: "/",
  /** 이용 방법 화면은 따로 없습니다. 랜딩의 진행 순서 섹션이 그 역할입니다. */
  howTo: "/#process",
  login: "/login",
  /** 로그인에 실패해 돌아올 때. `code` 는 서버의 실패 코드(KAKAO_AUTH_FAILED 등)입니다. */
  loginFailed: (code: string) => `/login?${ERROR_PARAM}=${encodeURIComponent(code)}`,
  /** 카카오 인가 시작. Route Handler(`app/api/auth/kakao`)가 카카오로 보냅니다. */
  kakaoStart: "/api/auth/kakao",

  developers: "/developers",
  developersByField: (codeId: number) =>
    `/developers?${FIELD_CODE_IDS_PARAM}=${codeId}`,
  developer: (developerId: number) => `/developers/${developerId}`,

  /**
   * 약관과 정책. `policies` 는 문서 목록이고 본문은 문서마다 따로입니다.
   * 목록 화면(`8_약관`)이 문서별로 「읽기」를 두고, 개인정보 처리방침처럼 주소를 따로
   * 제출해야 하는 문서가 있어 한 페이지에 모으지 않습니다. 서버가 주는 문서가 아니라
   * 화면이 가진 정적 문서입니다. (화면 없음 — 별도 이슈)
   */
  policies: "/policies",
  policiesTerms: "/policies/terms",
  policiesPrivacy: "/policies/privacy",
  policiesElectronicFinance: "/policies/electronic-finance",
  policiesRefundDispute: "/policies/refund-dispute",

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

  /** 방 대화. 상담·컨설팅·개발 방이 같은 주소를 씁니다. */
  room: (roomId: number) => `/rooms/${roomId}`,
  /** 개발자 추천 결과. 요구사항을 고르면 만들어지는 회차입니다. */
  recommendation: (batchId: number) => `/recommendations/${batchId}`,

  /** 마이페이지. 계정·계좌·프로젝트·개발자 프로필·알림 설정을 한 화면에서 봅니다. */
  mypage: "/mypage",
  /** 회원 탈퇴. 마이페이지의 「탈퇴하기」가 옵니다. */
  mypageWithdrawal: "/mypage/withdrawal",

  // ---- 아래는 아직 화면이 없는 임시 경로입니다. 화면이 생기면 값만 고칩니다. ----
  /** 의뢰 목록. 방 화면(/rooms/{roomId})의 진입점이 됩니다. */
  requests: "/requests",
  settlements: "/settlements",
  /** 푸터 "정산 방식" 안내. */
  settlementsGuide: "/settlements/guide",
  notifications: "/notifications",
  /** 계좌 등록. 마이페이지의 「계좌 변경」이 옵니다. */
  accountRegister: "/mypage/account",
  /** 내 개발자 프로필. 푸터 "프로필 등록"도 여기로 옵니다. */
  mypageProfile: "/mypage/profile",
  /** 내 일정. 마이페이지의 「일정 관리」가 옵니다. */
  mypageSchedule: "/mypage/schedule",
  mypageSettings: "/mypage/settings",
} as const;
