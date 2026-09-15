/**
 * 화면 경로 상수. 컴포넌트가 URL 문자열을 직접 조립하지 않습니다.
 *
 * API 경로는 `lib/api/endpoints.ts`, 화면 경로는 여기입니다.
 * 목적지가 바뀌면 이 파일만 고칩니다.
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
  /** 약관과 정책은 한 페이지입니다. 문서별 링크는 앵커로 구분합니다. */
  policies: "/policies",
  policiesTerms: "/policies#terms",
  policiesPrivacy: "/policies#privacy",
  policiesDispute: "/policies#dispute",
} as const;
