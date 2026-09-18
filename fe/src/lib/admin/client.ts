import axios from "axios";
import { attachErrorNormalizer } from "@/lib/api/interceptors";

const TIMEOUT_MS = 10_000;

/**
 * 브라우저 → 자기 도메인(`/api/admin`). 운영 콘솔 전용 axios 인스턴스입니다.
 *
 * **사용자용 `clientApi` 를 쓰지 않습니다.** 그쪽에는 401 을 만나면 사용자 토큰을
 * 재발급하고(`/api/auth/refresh`) 실패하면 「세션이 만료됐다」 이벤트를 쏘는
 * 인터셉터가 붙어 있습니다(`lib/api/client.ts`). 운영 콘솔에서 401 이 나는 것은
 * **운영자 토큰**이 없거나 만료된 것이라, 그 인터셉터를 타면
 *
 * - 엉뚱한 사용자 토큰을 재발급하고
 * - 로그인해 있던 사용자를 로그아웃시키고
 * - 운영자는 카카오 로그인 화면으로 끌려갑니다.
 *
 * 그래서 오류 정규화만 붙이고 재발급은 붙이지 않습니다. 운영 세션은 재발급 API가
 * 없어 만료되면 다시 로그인하는 것이 맞습니다(`lib/admin/session.ts`).
 */
export const adminClientApi = attachErrorNormalizer(
  axios.create({
    baseURL: "",
    timeout: TIMEOUT_MS,
    headers: { "Content-Type": "application/json" },
  }),
);
