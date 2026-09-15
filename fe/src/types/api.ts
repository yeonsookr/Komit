/**
 * 코밋 서버(`/service`)의 공통 응답 계약.
 * 목 Swagger `/service/docs` 의 ErrorResponse · FieldError 와 같은 모양입니다.
 * 에스크로(`/escrow`)는 `Header`/`REC` 형식이라 이 타입을 쓰지 않습니다.
 */

/** 실패 응답 본문. */
export interface ApiErrorBody {
  /** 화면 분기·재시도 판단에 쓰는 코드. 예: `ROOM_NOT_FOUND` */
  code: string;
  /** 사용자에게 그대로 보여줄 수 있는 문구. */
  message: string;
  /** 422 검증 실패일 때만 옵니다. */
  errors?: FieldError[];
}

/** 검증에 실패한 필드 하나. */
export interface FieldError {
  field: string;
  reason: string;
}

/**
 * 화면이 받는 정규화된 에러. interceptor 가 axios 에러를 전부 이걸로 바꿉니다.
 * 화면마다 axios 에러 구조를 해석하지 않기 위한 경계입니다.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly errors?: FieldError[];

  constructor(status: number, code: string, message: string, errors?: FieldError[]) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}

/** 네트워크가 끊겼거나 응답 자체가 오지 않았을 때. */
export const NETWORK_ERROR_CODE = "NETWORK_ERROR";
/** 응답은 왔지만 계약에 없는 모양일 때. */
export const UNKNOWN_ERROR_CODE = "UNKNOWN_ERROR";
