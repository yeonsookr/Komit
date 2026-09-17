/**
 * API 경로 상수. 화면과 Route Handler 가 URL 문자열을 직접 조립하지 않습니다.
 *
 * `BACKEND` — Next 서버가 코밋 서버를 부르는 경로. base URL 뒤에 붙습니다.
 *             목의 `/service` 접두사는 base URL 에 들어 있으므로 여기 적지 않습니다.
 * `INTERNAL` — 브라우저가 Next 서버를 부르는 경로. 언제나 `/api` 아래입니다.
 */

export const BACKEND = {
  AUTH_KAKAO: "/api/auth/kakao",
  AUTH_TOKEN_REFRESH: "/api/auth/token/refresh",
  AUTH_LOGOUT: "/api/auth/logout",
  LANDING_SUMMARY: "/api/landing/summary",
  LANDING_FEATURED: "/api/landing/featured-developers",
  ME: "/api/users/me",
  ROOMS: "/api/rooms",
  ROOM: (roomId: number) => `/api/rooms/${roomId}`,
  ROOM_CONVERSION: (roomId: number) => `/api/rooms/${roomId}/conversion`,
  DEVELOPERS: "/api/developers",
  DEVELOPER: (developerId: number) => `/api/developers/${developerId}`,
  DEVELOPER_REVIEWS: (developerId: number) => `/api/developers/${developerId}/reviews`,
  CODES: (groupKey: string) => `/api/codes/${groupKey}`,
  RECOMMENDATION: (batchId: number) => `/api/recommendations/${batchId}`,
  CONSULTATION_REQUESTS: (batchId: number) =>
    `/api/recommendations/${batchId}/consultation-requests`,
  DIAGNOSES: "/api/diagnoses",
  SURVEY: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/survey`,
  SURVEY_ANSWERS: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/survey/answers`,
  SURVEY_COMPLETE: (diagnosisId: number) =>
    `/api/diagnoses/${diagnosisId}/survey/complete`,
  INSPECTIONS: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/inspections`,
  SCAN_FILE: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/scan-file`,
  DIAGNOSIS: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}`,
  ISSUES: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/issues`,
  ISSUE: (issueId: number) => `/api/issues/${issueId}`,
  REQUIREMENTS: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/requirements`,
  RECOMMENDATIONS: (projectId: number) => `/api/projects/${projectId}/recommendations`,
  MYPAGE: "/api/mypage",
  /** 알림톡 수신 설정. 마이페이지의 알림 칸이 씁니다. */
  NOTIFICATION_SETTING: "/api/settings/notification",
  /** 탈퇴 전 확인(GET)과 탈퇴(POST). 계정을 지우는 것이 아니라 삭제 표시를 켭니다. */
  WITHDRAWAL: "/api/users/me/withdrawal",
  CONTRACT: (contractId: number) => `/api/contracts/${contractId}`,
  CONTRACT_SIGNATURES: (contractId: number) => `/api/contracts/${contractId}/signatures`,
  CONTRACT_DEPOSIT: (contractId: number) => `/api/contracts/${contractId}/deposit`,
  CONTRACT_START: (contractId: number) => `/api/contracts/${contractId}/start`,
  CONTRACT_REVIEW: (contractId: number) => `/api/contracts/${contractId}/review`,
  CONTRACT_LEDGER: (contractId: number) => `/api/contracts/${contractId}/ledger`,
  /** 「7 정산」 — 소비는 달을 받고, 소득·예치금은 달 파라미터가 없습니다. */
  SETTLEMENT_SPENDING: "/api/settlements/spending",
  SETTLEMENT_INCOME: "/api/settlements/income",
  SETTLEMENT_DEPOSITS: "/api/settlements/deposits",
  DEVELOPMENT_CONTRACTS: (roomId: number) => `/api/rooms/${roomId}/development-contracts`,
  MILESTONE_SUBMISSION: (milestoneId: number) => `/api/milestones/${milestoneId}/submission`,
  MILESTONE_QA_RESULTS: (milestoneId: number) => `/api/milestones/${milestoneId}/qa-results`,
  MILESTONE_CONFIRMATION: (milestoneId: number) =>
    `/api/milestones/${milestoneId}/confirmation`,
  DELIVERABLE_FILE: (deliverableId: number) => `/api/deliverables/${deliverableId}/file`,
  PAYMENT_METHODS: "/api/payment-methods",
  UPLOADS: "/api/uploads",
  UPLOAD_COMPLETE: (uploadId: number) => `/api/uploads/${uploadId}/complete`,
} as const;

export const INTERNAL = {
  /** 카카오 인가 시작. 화면 링크는 `ROUTES.kakaoStart` 로 같은 값을 가리킵니다. */
  AUTH_KAKAO: "/api/auth/kakao",
  /** 카카오가 인가 코드를 돌려주는 곳. 콘솔의 Redirect URI 와 같아야 합니다. */
  AUTH_KAKAO_CALLBACK: "/api/auth/kakao/callback",
  AUTH_REFRESH: "/api/auth/refresh",
  AUTH_LOGOUT: "/api/auth/logout",
  SURVEY: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/survey`,
  ME: "/api/users/me",
  ROOMS: "/api/rooms",
  ROOM: (roomId: number) => `/api/rooms/${roomId}`,
  ROOM_MESSAGES: (roomId: number) => `/api/rooms/${roomId}/messages`,
  ROOM_CONVERSION: (roomId: number) => `/api/rooms/${roomId}/conversion`,
  DEVELOPERS: "/api/developers",
  DEVELOPER: (developerId: number) => `/api/developers/${developerId}`,
  CONSULTATION_REQUESTS: (batchId: number) =>
    `/api/recommendations/${batchId}/consultation-requests`,
  DIAGNOSES: "/api/diagnoses",
  SURVEY_ANSWERS: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/survey/answers`,
  SURVEY_COMPLETE: (diagnosisId: number) =>
    `/api/diagnoses/${diagnosisId}/survey/complete`,
  INSPECTIONS: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/inspections`,
  ISSUES: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/issues`,
  ISSUE: (issueId: number) => `/api/issues/${issueId}`,
  REQUIREMENTS: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/requirements`,
  RECOMMENDATIONS: (projectId: number) => `/api/projects/${projectId}/recommendations`,
  SCAN_FILE: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/scan-file`,
  MY_NAME: "/api/users/me",
  NOTIFICATION_SETTING: "/api/settings/notification",
  /** 회원 탈퇴. 탈퇴 화면의 「탈퇴하기」가 부릅니다. */
  WITHDRAWAL: "/api/users/me/withdrawal",
  CONTRACT_SIGNATURES: (contractId: number) => `/api/contracts/${contractId}/signatures`,
  CONTRACT_DEPOSIT: (contractId: number) => `/api/contracts/${contractId}/deposit`,
  CONTRACT_START: (contractId: number) => `/api/contracts/${contractId}/start`,
  CONTRACT_REVIEW: (contractId: number) => `/api/contracts/${contractId}/review`,
  DEVELOPMENT_CONTRACTS: (roomId: number) => `/api/rooms/${roomId}/development-contracts`,
  MILESTONE_SUBMISSION: (milestoneId: number) => `/api/milestones/${milestoneId}/submission`,
  MILESTONE_QA_RESULTS: (milestoneId: number) => `/api/milestones/${milestoneId}/qa-results`,
  MILESTONE_CONFIRMATION: (milestoneId: number) =>
    `/api/milestones/${milestoneId}/confirmation`,
  DELIVERABLE_FILE: (deliverableId: number) => `/api/deliverables/${deliverableId}/file`,
} as const;
