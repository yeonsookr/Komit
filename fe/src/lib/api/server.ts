import "server-only";

import { Readable } from "node:stream";
import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from "axios";
import { requireServerEnv } from "@/lib/env";
import { authorized } from "@/lib/auth/session";
import { attachErrorNormalizer } from "./interceptors";

/** 일반 API. 목·실서버 모두 이 안에서 끝나야 합니다. */
const DEFAULT_TIMEOUT_MS = 5_000;

/**
 * 파일 내려받기가 응답 머리를 받을 때까지 기다리는 시간. 본문은 스트림으로 흘려보내므로
 * 머리를 받은 뒤에는 이 제한이 걸리지 않습니다. RULES.md 에 없는 운영 값입니다.
 */
const DOWNLOAD_TIMEOUT_MS = 30_000;

/** 내려받기가 실패했을 때 사유를 읽으려고 모으는 본문의 최대 크기. 실패 응답 JSON 은 이보다 작습니다. */
const DOWNLOAD_ERROR_BODY_LIMIT_BYTES = 64 * 1024;

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
function createApi(
  envName: string,
  timeout: number,
  beforeNormalize?: (instance: AxiosInstance) => void,
): AxiosInstance {
  const instance = axios.create({
    baseURL: requireServerEnv(envName),
    timeout,
    headers: { "Content-Type": "application/json" },
  });
  // 응답 interceptor 는 붙인 순서대로 돕니다. 정규화보다 먼저 손볼 것은 여기서 붙입니다.
  beforeNormalize?.(instance);
  return attachErrorNormalizer(instance);
}

/** 요청 설정에 현재 사용자 토큰을 넣습니다. */
async function injectSessionAuthorization(
  config: InternalAxiosRequestConfig,
): Promise<InternalAxiosRequestConfig> {
  // 카카오 로그인 직후처럼 아직 세션 쿠키를 굽기 전 받은 토큰을 명시한 요청은
  // 그대로 사용합니다. 그 외 요청만 현재 사용자 세션에서 인증을 가져옵니다.
  if (!config.headers.has("Authorization")) {
    const { headers } = await authorized();
    config.headers.set("Authorization", headers.Authorization);
  }
  return config;
}

/** 스트림 본문을 한도까지만 모읍니다. 한도를 넘거나 읽다 끊기면 null 입니다. */
async function readLimited(stream: Readable, limit: number): Promise<Buffer | null> {
  const chunks: Buffer[] = [];
  let size = 0;
  try {
    for await (const chunk of stream) {
      const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk as Uint8Array);
      size += bytes.length;
      if (size > limit) {
        stream.destroy();
        return null;
      }
      chunks.push(bytes);
    }
    return Buffer.concat(chunks);
  } catch {
    return null;
  }
}

/**
 * 스트림으로 받은 요청이 실패하면 본문을 바이트로 모아 둡니다.
 *
 * 공통 정규화는 바이트 본문만 JSON 으로 풀어 읽습니다. 스트림 그대로 두면 409 사유가
 * 고정 문구로 덮이고, 읽지 않은 연결도 남습니다.
 */
function bufferStreamErrorBody(instance: AxiosInstance): void {
  instance.interceptors.response.use(undefined, async (error: unknown) => {
    const response = axios.isAxiosError(error) ? error.response : undefined;
    if (response && response.data instanceof Readable) {
      response.data = await readLimited(response.data, DOWNLOAD_ERROR_BODY_LIMIT_BYTES);
    }
    throw error;
  });
}

let backend: AxiosInstance | null = null;
let authenticatedBackend: AxiosInstance | null = null;
let authenticatedDownload: AxiosInstance | null = null;

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
  instance.interceptors.request.use(injectSessionAuthorization);

  authenticatedBackend = instance;
  return authenticatedBackend;
}

/**
 * 로그인한 사용자가 파일을 내려받을 때 씁니다.
 *
 * 수십 MB 인계 파일도 메모리에 모으지 않도록 `responseType: "stream"` 으로 부르는
 * 전제입니다. 응답 머리까지만 `DOWNLOAD_TIMEOUT_MS` 를 기다립니다. 본문 압축을 풀면
 * 백엔드가 준 Content-Length 와 실제 크기가 어긋나므로 압축하지 않은 본문을 요청합니다.
 */
export function authenticatedDownloadApi(): AxiosInstance {
  if (authenticatedDownload) return authenticatedDownload;

  const instance = createApi("BACKEND_API_BASE_URL", DOWNLOAD_TIMEOUT_MS, bufferStreamErrorBody);
  instance.defaults.headers.common["Accept-Encoding"] = "identity";
  instance.interceptors.request.use(injectSessionAuthorization);

  authenticatedDownload = instance;
  return authenticatedDownload;
}
