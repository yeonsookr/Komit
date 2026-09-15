/**
 * 화면 경로 상수. 컴포넌트가 URL 문자열을 직접 조립하지 않습니다.
 * (docs/FRONTEND_CONVENTIONS.md 8.1)
 *
 * API 경로는 `lib/api/endpoints.ts`, 화면 경로는 여기입니다.
 * 목적지가 바뀌면 이 파일만 고칩니다. 아직 화면이 없는 목적지는 주석으로 표시합니다.
 */
export const ROUTES = {
  home: "/",
  /** 이용 방법 화면은 따로 없습니다. 랜딩의 진행 순서 섹션이 그 역할입니다. */
  howTo: "/#process",
  login: "/login",
  /** 카카오 인가 시작. BE 단계에서 Route Handler 가 붙습니다. 지금은 링크만 둡니다. */
  kakaoStart: "/api/auth/kakao",

  developers: "/developers",
  developersByField: (codeId: number) => `/developers?fieldCodeIds=${codeId}`,
  developer: (developerId: number) => `/developers/${developerId}`,

  /** 약관과 정책은 한 페이지입니다. 문서별 링크는 앵커로 구분합니다. (화면 없음) */
  policies: "/policies",
  policiesTerms: "/policies#terms",
  policiesPrivacy: "/policies#privacy",
  policiesDispute: "/policies#dispute",

  // ---- 아래는 아직 화면이 없는 임시 경로입니다. 화면이 생기면 값만 고칩니다. ----
  /** 의뢰 목록. 방 화면(/rooms/{roomId})의 진입점이 됩니다. */
  requests: "/requests",
  /** 사전점검(진단) 시작. 로그인이 필요합니다. */
  checkup: "/checkup",
  settlements: "/settlements",
  /** 푸터 "정산 방식" 안내. */
  settlementsGuide: "/settlements/guide",
  notifications: "/notifications",
  mypage: "/mypage",
  /** 내 개발자 프로필. 푸터 "프로필 등록"도 여기로 옵니다. */
  mypageProfile: "/mypage/profile",
  mypageSchedule: "/mypage/schedule",
  mypageSettings: "/mypage/settings",
} as const;
