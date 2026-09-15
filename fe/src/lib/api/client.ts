import axios from "axios";
import { attachErrorNormalizer } from "./interceptors";

const TIMEOUT_MS = 10_000;

/**
 * 브라우저 → 자기 도메인(`/api`).
 * 외부 서버 주소는 여기 오지 않습니다. 실제 호출은 Route Handler 가 대신합니다.
 * 컴포넌트는 이 인스턴스도 직접 쓰지 말고 services/ 의 요청 함수를 부릅니다.
 */
export const clientApi = attachErrorNormalizer(
  axios.create({
    baseURL: "",
    timeout: TIMEOUT_MS,
    headers: { "Content-Type": "application/json" },
  }),
);

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
