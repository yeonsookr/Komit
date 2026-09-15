import "server-only";

import { NextResponse } from "next/server";
import { ApiError, UNKNOWN_ERROR_CODE } from "@/types/api";

/**
 * Route Handler 의 실패 응답.
 * 백엔드가 준 code·message 만 그대로 넘기고 스택 트레이스, 내부 호스트명,
 * 디버그 필드는 브라우저로 보내지 않습니다.
 */
export function toErrorResponse(error: unknown): NextResponse {
  if (error instanceof ApiError) {
    // 네트워크 실패(status 0)는 게이트웨이 오류로 바꿔 내보냅니다.
    const status = error.status > 0 ? error.status : 502;
    return NextResponse.json(
      { code: error.code, message: error.message, ...(error.errors && { errors: error.errors }) },
      { status },
    );
  }

  // 환경 변수 누락처럼 우리 쪽 문제는 원인을 감추고 서버 로그로만 남깁니다.
  console.error("[route] 처리하지 못한 오류", error);
  return NextResponse.json(
    { code: UNKNOWN_ERROR_CODE, message: "요청을 처리하지 못했습니다." },
    { status: 500 },
  );
}

/** 경로 파라미터를 숫자로 읽습니다. 숫자가 아니면 null. */
export function parseNumericParam(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  return Number(value);
}
