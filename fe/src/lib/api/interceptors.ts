import axios, { type AxiosInstance } from "axios";
import { unstable_rethrow } from "next/navigation";
import {
  ApiError,
  NETWORK_ERROR_CODE,
  UNKNOWN_ERROR_CODE,
  type ApiErrorBody,
} from "@/types/api";

/** 서버가 준 body 가 코밋 실패 응답 모양인지 봅니다. */
function isApiErrorBody(body: unknown): body is ApiErrorBody {
  if (typeof body !== "object" || body === null) return false;
  const candidate = body as Record<string, unknown>;
  return typeof candidate.code === "string" && typeof candidate.message === "string";
}

/**
 * 파일을 arraybuffer 로 받는 요청은 실패 본문도 바이트로 옵니다. JSON 이면 풀어 읽고,
 * 아니면 받은 그대로 둡니다. 풀지 않으면 409 사유가 고정 문구로 덮입니다.
 */
function decodeBinaryBody(body: unknown): unknown {
  let bytes: Uint8Array | null = null;
  if (body instanceof ArrayBuffer) bytes = new Uint8Array(body);
  else if (ArrayBuffer.isView(body)) bytes = new Uint8Array(body.buffer, body.byteOffset, body.byteLength);
  if (bytes === null) return body;
  try {
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return body;
  }
}

/**
 * 모든 axios 에러를 ApiError 하나로 모읍니다.
 * 화면은 status 와 code 만 보면 되고 axios 의 내부 구조를 알 필요가 없습니다.
 */
export function attachErrorNormalizer(instance: AxiosInstance): AxiosInstance {
  instance.interceptors.response.use(
    (response) => response,
    (error: unknown) => {
      // cookies(), redirect() 같은 Next 내부 제어 신호는 앱 오류로 정규화하면 안 됩니다.
      // 특히 인증 Axios의 요청 interceptor가 cookies()를 읽을 때 정적 렌더링 전환
      // 신호가 이 경로를 지나므로 Next가 직접 처리하도록 가장 먼저 돌려보냅니다.
      unstable_rethrow(error);

      // 요청 interceptor가 인증 쿠키 부재처럼 이미 공통 오류로 판단한 경우에는
      // Axios 내부 오류가 아니라는 이유로 UNKNOWN_ERROR로 다시 덮지 않습니다.
      if (error instanceof ApiError) throw error;

      if (!axios.isAxiosError(error)) {
        throw new ApiError(0, UNKNOWN_ERROR_CODE, "알 수 없는 오류가 발생했습니다.");
      }

      const response = error.response;

      // 응답이 없으면 네트워크 문제이거나 timeout 입니다.
      if (!response) {
        throw new ApiError(
          0,
          NETWORK_ERROR_CODE,
          "서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        );
      }

      const body = decodeBinaryBody(response.data);
      if (isApiErrorBody(body)) {
        throw new ApiError(response.status, body.code, body.message, body.errors);
      }

      // 계약에 없는 모양이면 내부 정보를 그대로 흘리지 않고 고정 문구로 덮습니다.
      throw new ApiError(
        response.status,
        UNKNOWN_ERROR_CODE,
        "요청을 처리하지 못했습니다.",
      );
    },
  );

  return instance;
}
