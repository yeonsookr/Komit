import axios from "axios";
import { INTERNAL } from "./endpoints";
import { attachErrorNormalizer } from "./interceptors";
import { attachTokenRefresh } from "./refresh";
import { emitSessionExpired } from "@/lib/auth/sessionEvents";
import { clientBasePath } from "@/lib/basePath";

const TIMEOUT_MS = 10_000;

/** 인증 경로는 401 이 정상 응답일 수 있어(로그아웃 상태) 재발급 대상에서 뺍니다. */
const AUTH_PATH_PREFIX = "/api/auth/";

/**
 * 브라우저 → 자기 도메인(`/api`).
 * 외부 서버 주소는 여기 오지 않습니다. 실제 호출은 Route Handler 가 대신합니다.
 * 컴포넌트는 이 인스턴스도 직접 쓰지 말고 services/ 의 요청 함수를 부릅니다.
 *
 * 인터셉터 순서가 중요합니다. 재발급이 먼저, 정규화가 나중입니다 — 정규화된
 * ApiError 에는 원래 요청 설정이 없어 다시 보낼 수 없습니다.
 */
const instance = axios.create({
  // 기본 FE는 "", 테스트 FE는 "/test". 브라우저 API 호출 전체에 한 번만 적용합니다.
  baseURL: clientBasePath(),
  timeout: TIMEOUT_MS,
  headers: { "Content-Type": "application/json" },
});

attachTokenRefresh(instance, {
  // 이 인스턴스로 부르면 정규화 인터셉터를 거쳐 실패가 ApiError 로 옵니다. 여기서는
  // 성공·실패만 필요하므로 그대로 둡니다.
  refresh: () => instance.post(INTERNAL.AUTH_REFRESH).then(() => undefined),
  shouldRefresh: (url) => !url.startsWith(AUTH_PATH_PREFIX),
  onExpired: emitSessionExpired,
});

export const clientApi = attachErrorNormalizer(instance);

/**
 * 파일을 보낼 때는 공통 JSON 헤더를 지웁니다.
 *
 * FormData 는 경계 문자열이 들어간 multipart 헤더가 필요한데, 공통 헤더가
 * `application/json` 으로 덮여 있으면 서버가 본문을 읽지 못해 422 가 됩니다.
 * 헤더를 비우면 브라우저가 경계를 포함한 값을 직접 채웁니다.
 */
clientApi.interceptors.request.use((config) => {
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    delete config.headers["Content-Type"];
  }
  return config;
});
