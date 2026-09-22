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
  SIGNUP_COMPLETION: "/api/auth/signup/completion",
  REQUIRED_TERMS: "/api/settings/terms",
  LANDING_SUMMARY: "/api/landing/summary",
  LANDING_FEATURED: "/api/landing/featured-developers",
  ME: "/api/users/me",
  PROFILE_IMAGE: "/api/users/me/profile-image",
  MY_DEVELOPER_PROFILE: "/api/users/me/developer-profile",
  REVIEW_REPLY: (reviewId: number) => `/api/reviews/${reviewId}/reply`,
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
  MY_SESSIONS: "/api/users/me/sessions",
  /** 회원가입 때 발급한 계좌. 계좌 등록 화면의 「계좌 연동하기」가 씁니다. (R-ACC-14) */
  MEMBER_ACCOUNT: "/api/accounts/member-account",
  ACCOUNT_VERIFICATIONS: "/api/accounts/verifications",
  ACCOUNT_VERIFICATION_CONFIRM: (verificationId: number) =>
    `/api/accounts/verifications/${verificationId}/confirm`,
  /** 알림톡 수신 설정. 마이페이지의 알림 칸이 씁니다. */
  NOTIFICATION_SETTING: "/api/settings/notification",
  NOTIFICATIONS: "/api/notifications",
  NOTIFICATIONS_READ_ALL: "/api/notifications/read-all",
  NOTIFICATION_READ: (notificationId: number) =>
    `/api/notifications/${notificationId}/read`,
  /** 탈퇴 전 확인(GET)과 탈퇴(POST). 계정을 지우는 것이 아니라 삭제 표시를 켭니다. */
  WITHDRAWAL: "/api/users/me/withdrawal",
  CONTRACT: (contractId: number) => `/api/contracts/${contractId}`,
  CONTRACT_SIGNATURES: (contractId: number) => `/api/contracts/${contractId}/signatures`,
  CONTRACT_DEPOSIT: (contractId: number) => `/api/contracts/${contractId}/deposit`,
  CONTRACT_START: (contractId: number) => `/api/contracts/${contractId}/start`,
  CONTRACT_REVIEW: (contractId: number) => `/api/contracts/${contractId}/review`,
  CONTRACT_REPORTS: (contractId: number) => `/api/contracts/${contractId}/reports`,
  CONTRACT_LEDGER: (contractId: number) => `/api/contracts/${contractId}/ledger`,
  /** 「7 정산」 — 소비는 달을 받고, 소득·예치금은 달 파라미터가 없습니다. */
  SETTLEMENT_SPENDING: "/api/settlements/spending",
  SETTLEMENT_INCOME: "/api/settlements/income",
  SETTLEMENT_DEPOSITS: "/api/settlements/deposits",
  DEVELOPMENT_CONTRACTS: (roomId: number) => `/api/rooms/${roomId}/development-contracts`,
  /**
   * 컨설팅(영역 4). 개발 계약과 달리 한 번 만나는 세션이 계약의 전부라,
   * 계약서는 방에서 만들고 그 뒤는 세션 식별자로 다룹니다.
   */
  CONSULTING_CONTRACTS: (roomId: number) => `/api/rooms/${roomId}/consulting-contracts`,
  CONSULTING_SESSION: (sessionId: number) => `/api/consulting-sessions/${sessionId}`,
  SESSION_PARTICIPATIONS: (sessionId: number) =>
    `/api/consulting-sessions/${sessionId}/participations`,
  SESSION_END: (sessionId: number) => `/api/consulting-sessions/${sessionId}/end`,
  SESSION_RECORDINGS: (sessionId: number) =>
    `/api/consulting-sessions/${sessionId}/recordings`,
  SESSION_SUMMARY: (sessionId: number) => `/api/consulting-sessions/${sessionId}/summary`,
  SESSION_SUMMARY_CHANGES: (sessionId: number) =>
    `/api/consulting-sessions/${sessionId}/summary/applied-changes`,
  MILESTONE_SUBMISSION: (milestoneId: number) => `/api/milestones/${milestoneId}/submission`,
  MILESTONE_QA_RESULTS: (milestoneId: number) => `/api/milestones/${milestoneId}/qa-results`,
  MILESTONE_CONFIRMATION: (milestoneId: number) =>
    `/api/milestones/${milestoneId}/confirmation`,
  DELIVERABLE_FILE: (deliverableId: number) => `/api/deliverables/${deliverableId}/file`,
  REALTIME_TICKETS: "/api/realtime/tickets",
  PAYMENT_METHODS: "/api/payment-methods",
  UPLOADS: "/api/uploads",
  UPLOAD_COMPLETE: (uploadId: number) => `/api/uploads/${uploadId}/complete`,
  /**
   * 운영 콘솔(영역 9). 사용자 토큰으로는 부를 수 없고 운영 로그인으로 받은 토큰만
   * 통합니다. 붙이는 자리는 `lib/admin/session.ts` 의 `adminAuthorized()` 입니다.
   */
  ADMIN_LOGIN: "/api/admin/login",
  ADMIN_SUMMARY: "/api/admin/summary",
  ADMIN_REPORTS: "/api/admin/reports",
  ADMIN_REPORT: (reportId: number) => `/api/admin/reports/${reportId}`,
  ADMIN_REPORT_RESOLVE: (reportId: number) => `/api/admin/reports/${reportId}/resolve`,
  ADMIN_RECONCILIATION: "/api/admin/reconciliation",
  ADMIN_CONTRACT_TEMPLATES: "/api/admin/contract-templates",
  ADMIN_CONTRACT_TEMPLATE: (templateId: number) =>
    `/api/admin/contract-templates/${templateId}`,
  ADMIN_CONTRACT_TEMPLATE_PREVIEW: "/api/admin/contract-templates/preview",
} as const;

export const INTERNAL = {
  /** 카카오 인가 시작. 화면 링크는 `ROUTES.kakaoStart` 로 같은 값을 가리킵니다. */
  AUTH_KAKAO: "/api/auth/kakao",
  /** 카카오가 인가 코드를 돌려주는 곳. 콘솔의 Redirect URI 와 같아야 합니다. */
  AUTH_KAKAO_CALLBACK: "/api/auth/kakao/callback",
  AUTH_REFRESH: "/api/auth/refresh",
  AUTH_LOGOUT: "/api/auth/logout",
  SIGNUP_COMPLETION: "/api/auth/signup/completion",
  REQUIRED_TERMS: "/api/settings/terms",
  SURVEY: (diagnosisId: number) => `/api/diagnoses/${diagnosisId}/survey`,
  ME: "/api/users/me",
  PROFILE_IMAGE: "/api/users/me/profile-image",
  MY_DEVELOPER_PROFILE: "/api/users/me/developer-profile",
  REVIEW_REPLY: (reviewId: number) => `/api/reviews/${reviewId}/reply`,
  PROFILE_UPLOAD: "/api/users/me/developer-profile/upload",
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
  /** 회원가입 때 발급한 계좌. 계좌 등록 화면의 「계좌 연동하기」가 씁니다. (R-ACC-14) */
  MEMBER_ACCOUNT: "/api/accounts/member-account",
  ACCOUNT_VERIFICATIONS: "/api/accounts/verifications",
  ACCOUNT_VERIFICATION_CONFIRM: (verificationId: number) =>
    `/api/accounts/verifications/${verificationId}/confirm`,
  NOTIFICATION_SETTING: "/api/settings/notification",
  NOTIFICATIONS_READ_ALL: "/api/notifications/read-all",
  NOTIFICATION_READ: (notificationId: number) =>
    `/api/notifications/${notificationId}/read`,
  /** 회원 탈퇴. 탈퇴 화면의 「탈퇴하기」가 부릅니다. */
  WITHDRAWAL: "/api/users/me/withdrawal",
  CONTRACT_SIGNATURES: (contractId: number) => `/api/contracts/${contractId}/signatures`,
  CONTRACT_DEPOSIT: (contractId: number) => `/api/contracts/${contractId}/deposit`,
  CONTRACT_START: (contractId: number) => `/api/contracts/${contractId}/start`,
  CONTRACT_REVIEW: (contractId: number) => `/api/contracts/${contractId}/review`,
  CONTRACT_REPORTS: (contractId: number) => `/api/contracts/${contractId}/reports`,
  DEVELOPMENT_CONTRACTS: (roomId: number) => `/api/rooms/${roomId}/development-contracts`,
  /**
   * 컨설팅(영역 4). 개발 계약과 달리 한 번 만나는 세션이 계약의 전부라,
   * 계약서는 방에서 만들고 그 뒤는 세션 식별자로 다룹니다.
   */
  /** 계약서 한 건. 컨설팅 계약서를 고칠 때 PATCH 로 부릅니다. */
  CONTRACT: (contractId: number) => `/api/contracts/${contractId}`,
  CONSULTING_CONTRACTS: (roomId: number) => `/api/rooms/${roomId}/consulting-contracts`,
  CONSULTING_SESSION: (sessionId: number) => `/api/consulting-sessions/${sessionId}`,
  SESSION_PARTICIPATIONS: (sessionId: number) =>
    `/api/consulting-sessions/${sessionId}/participations`,
  SESSION_END: (sessionId: number) => `/api/consulting-sessions/${sessionId}/end`,
  SESSION_RECORDINGS: (sessionId: number) =>
    `/api/consulting-sessions/${sessionId}/recordings`,
  SESSION_SUMMARY: (sessionId: number) => `/api/consulting-sessions/${sessionId}/summary`,
  /**
   * 파일 올리기 3콜 중 첫째와 셋째. 가운데 PUT 은 브라우저가 저장소로 직접 보내므로
   * 여기에 없습니다.
   */
  UPLOADS: "/api/uploads",
  UPLOAD_COMPLETE: (uploadId: number) => `/api/uploads/${uploadId}/complete`,
  SESSION_SUMMARY_CHANGES: (sessionId: number) =>
    `/api/consulting-sessions/${sessionId}/summary/applied-changes`,
  MILESTONE_SUBMISSION: (milestoneId: number) => `/api/milestones/${milestoneId}/submission`,
  MILESTONE_QA_RESULTS: (milestoneId: number) => `/api/milestones/${milestoneId}/qa-results`,
  MILESTONE_CONFIRMATION: (milestoneId: number) =>
    `/api/milestones/${milestoneId}/confirmation`,
  DELIVERABLE_FILE: (deliverableId: number) => `/api/deliverables/${deliverableId}/file`,
  REALTIME_TICKETS: "/api/realtime/tickets",
  /**
   * 운영 콘솔. **누른 뒤에야 생기는 요청만 있습니다** — 목록·상세·본문은 서버
   * 컴포넌트가 직접 읽어 내려보내므로 브라우저가 부를 일이 없고, 부를 일이 없는
   * 경로를 적어 두면 쓰지 않는 문을 열어 두는 것과 같습니다(컨벤션 5장: Route
   * Handler 는 필요한 경로·메서드만 명시적으로 연다).
   */
  ADMIN_LOGIN: "/api/admin/login",
  ADMIN_LOGOUT: "/api/admin/logout",
  ADMIN_REPORT_RESOLVE: (reportId: number) => `/api/admin/reports/${reportId}/resolve`,
  ADMIN_CONTRACT_TEMPLATES: "/api/admin/contract-templates",
  ADMIN_CONTRACT_TEMPLATE_PREVIEW: "/api/admin/contract-templates/preview",
} as const;
