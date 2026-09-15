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
