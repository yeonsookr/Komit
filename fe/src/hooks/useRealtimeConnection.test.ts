import { describe, expect, it } from "vitest";
import { ApiError } from "@/types/api";
import {
  isPermanentTicketError,
  isTerminalRealtimeCloseCode,
  isTerminalRealtimeType,
} from "./useRealtimeConnection";

describe("isPermanentTicketError", () => {
  it.each([401, 403, 404, 409, 422])("%i 응답은 재시도하지 않는다", (status) => {
    expect(isPermanentTicketError(new ApiError(status, "DENIED", "입장할 수 없습니다."))).toBe(
      true,
    );
  });

  it.each([0, 500, 502, 503])("%i 실패는 일시 실패로 보고 재시도한다", (status) => {
    expect(isPermanentTicketError(new ApiError(status, "TEMPORARY", "잠시 후 다시"))).toBe(
      false,
    );
  });
});

describe("terminal realtime 종료", () => {
  it("SYSTEM_REPLACED는 재연결하지 않는 제어 메시지다", () => {
    expect(isTerminalRealtimeType("SYSTEM_REPLACED")).toBe(true);
    expect(isTerminalRealtimeType("SYSTEM_ERROR")).toBe(false);
  });

  it("4001만 중복 연결 교체 종료로 분류한다", () => {
    expect(isTerminalRealtimeCloseCode(4001)).toBe(true);
    expect(isTerminalRealtimeCloseCode(1006)).toBe(false);
  });
});
