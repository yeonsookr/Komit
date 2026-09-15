import axios, { type AxiosInstance } from "axios";
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
 * 모든 axios 에러를 ApiError 하나로 모읍니다.
 * 화면은 status 와 code 만 보면 되고 axios 의 내부 구조를 알 필요가 없습니다.
 */
export function attachErrorNormalizer(instance: AxiosInstance): AxiosInstance {
  instance.interceptors.response.use(
    (response) => response,
    (error: unknown) => {
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

      if (isApiErrorBody(response.data)) {
        throw new ApiError(
          response.status,
          response.data.code,
          response.data.message,
          response.data.errors,
        );
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
