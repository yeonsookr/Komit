/**
 * API 경로 상수. 화면과 Route Handler 가 URL 문자열을 직접 조립하지 않습니다.
 *
 * `BACKEND` — Next 서버가 코밋 서버를 부르는 경로. base URL 뒤에 붙습니다.
 *             목의 `/service` 접두사는 base URL 에 들어 있으므로 여기 적지 않습니다.
 * `INTERNAL` — 브라우저가 Next 서버를 부르는 경로. 언제나 `/api` 아래입니다.
 */

export const BACKEND = {
  LANDING_SUMMARY: "/api/landing/summary",
  LANDING_FEATURED: "/api/landing/featured-developers",
  ME: "/api/users/me",
  ROOMS: "/api/rooms",
  ROOM: (roomId: number) => `/api/rooms/${roomId}`,
  DEVELOPERS: "/api/developers",
  DEVELOPER: (developerId: number) => `/api/developers/${developerId}`,
  DEVELOPER_REVIEWS: (developerId: number) => `/api/developers/${developerId}/reviews`,
  CODES: (groupKey: string) => `/api/codes/${groupKey}`,
  RECOMMENDATION: (batchId: number) => `/api/recommendations/${batchId}`,
  CONSULTATION_REQUESTS: (batchId: number) =>
    `/api/recommendations/${batchId}/consultation-requests`,
} as const;

export const INTERNAL = {
  ME: "/api/users/me",
  ROOMS: "/api/rooms",
  ROOM: (roomId: number) => `/api/rooms/${roomId}`,
  ROOM_MESSAGES: (roomId: number) => `/api/rooms/${roomId}/messages`,
  DEVELOPERS: "/api/developers",
  DEVELOPER: (developerId: number) => `/api/developers/${developerId}`,
  CONSULTATION_REQUESTS: (batchId: number) =>
    `/api/recommendations/${batchId}/consultation-requests`,
} as const;
