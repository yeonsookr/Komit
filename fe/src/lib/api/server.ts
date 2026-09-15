import "server-only";

import axios from "axios";
import { requireServerEnv } from "@/lib/env";
import { attachErrorNormalizer } from "./interceptors";

/** 일반 API. 목·실서버 모두 이 안에서 끝나야 합니다. */
const DEFAULT_TIMEOUT_MS = 5_000;
/** AI 서버는 응답이 느려 따로 잡습니다. */
const AI_TIMEOUT_MS = 30_000;

/**
 * Next 서버 → 코밋 백엔드.
 * 브라우저에서 import 하면 server-only 가 빌드를 실패시킵니다.
 * 환경 변수가 없으면 requireServerEnv 가 던져서 요청이 실패합니다.
 */
export const backendApi = attachErrorNormalizer(
  axios.create({
    baseURL: requireServerEnv("BACKEND_API_BASE_URL"),
    timeout: DEFAULT_TIMEOUT_MS,
    headers: { "Content-Type": "application/json" },
  }),
);

/** Next 서버 → AI 서버. */
export const aiApi = attachErrorNormalizer(
  axios.create({
    baseURL: requireServerEnv("AI_API_BASE_URL"),
    timeout: AI_TIMEOUT_MS,
    headers: { "Content-Type": "application/json" },
  }),
);
