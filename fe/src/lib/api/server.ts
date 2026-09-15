import "server-only";

import axios, { type AxiosInstance } from "axios";
import { requireServerEnv } from "@/lib/env";
import { attachErrorNormalizer } from "./interceptors";

/** 일반 API. 목·실서버 모두 이 안에서 끝나야 합니다. */
const DEFAULT_TIMEOUT_MS = 5_000;
/** AI 서버는 응답이 느려 따로 잡습니다. */
const AI_TIMEOUT_MS = 30_000;

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
let ai: AxiosInstance | null = null;

/**
 * Next 서버 → 코밋 백엔드.
 * 브라우저에서 import 하면 server-only 가 빌드를 실패시킵니다.
 */
export function backendApi(): AxiosInstance {
  backend ??= createApi("BACKEND_API_BASE_URL", DEFAULT_TIMEOUT_MS);
  return backend;
}

/** Next 서버 → AI 서버. */
export function aiApi(): AxiosInstance {
  ai ??= createApi("AI_API_BASE_URL", AI_TIMEOUT_MS);
  return ai;
}
