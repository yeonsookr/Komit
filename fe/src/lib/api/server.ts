import "server-only";

import axios, { type AxiosInstance } from "axios";
import { requireServerEnv } from "@/lib/env";
import { authorized } from "@/lib/auth/session";
import { attachErrorNormalizer } from "./interceptors";

/** 일반 API. 목·실서버 모두 이 안에서 끝나야 합니다. */
const DEFAULT_TIMEOUT_MS = 5_000;

/**
 * 인스턴스를 처음 쓸 때 만듭니다.
 *
 * **모듈을 읽는 시점에 환경 변수를 읽지 않습니다.** 빌드가 이 모듈을 불러오므로
 * 그때 읽으면 빌드에도 서버 주소가 있어야 하고, 결과 이미지에 주소가 박혀
 * 환경마다 다시 구워야 합니다. CI 는 이미지를 한 번 굽고 실행할 때 GitLab
 * CI/CD 변수를 주입하므로, 값은 첫 요청에서 읽습니다.
 *
 * 값이 없으면 안전하지 않은 기본값으로 진행하지 않고 그 요청을 실패시킵니다
 * (docs/FRONTEND_CONVENTIONS.md 5장).
 */
function createApi(envName: string, timeout: number): AxiosInstance {
  return attachErrorNormalizer(
    axios.create({
      baseURL: requireServerEnv(envName),
      timeout,
      headers: { "Content-Type": "application/json" },
    }),
  );
}

let backend: AxiosInstance | null = null;
let authenticatedBackend: AxiosInstance | null = null;

/**
 * Next 서버 → 코밋 백엔드.
 * 브라우저에서 import 하면 server-only 가 빌드를 실패시킵니다.
 */
export function backendApi(): AxiosInstance {
  backend ??= createApi("BACKEND_API_BASE_URL", DEFAULT_TIMEOUT_MS);
  return backend;
}

/**
 * 로그인한 사용자의 요청을 코밋 백엔드로 보냅니다.
 *
 * 토큰은 요청마다 현재 Next 요청의 httpOnly 쿠키에서 읽어 해당 Axios 요청 설정에만
 * 넣습니다. 인스턴스 기본 헤더를 바꾸지 않으므로 동시에 들어온 다른 사용자의 요청과
 * 토큰이 섞이지 않습니다. 공개 API는 `backendApi()`를 사용해 정적 렌더링 중에
 * 요청 의존 API인 `cookies()`를 읽지 않도록 분리합니다.
 */
export function authenticatedBackendApi(): AxiosInstance {
  if (authenticatedBackend) return authenticatedBackend;

  const instance = createApi("BACKEND_API_BASE_URL", DEFAULT_TIMEOUT_MS);
  instance.interceptors.request.use(async (config) => {
    // 카카오 로그인 직후처럼 아직 세션 쿠키를 굽기 전 받은 토큰을 명시한 요청은
    // 그대로 사용합니다. 그 외 요청만 현재 사용자 세션에서 인증을 가져옵니다.
    if (!config.headers.has("Authorization")) {
      const { headers } = await authorized();
      config.headers.set("Authorization", headers.Authorization);
    }
    return config;
  });

  authenticatedBackend = instance;
  return authenticatedBackend;
}
