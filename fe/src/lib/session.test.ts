import { describe, expect, it } from "vitest";
import {
  confirmedSession,
  sessionElapsedMinutes,
  sessionPhase,
  SESSION_OPEN_BEFORE_MINUTES,
} from "./session";
import type { ConsultingSession, Contract } from "@/types/contract";

const SCHEDULED_AT = "2026-08-29T14:00:00+09:00";

function session(overrides: Partial<ConsultingSession> = {}): ConsultingSession {
  return {
    sessionId: 55,
    contractId: 120,
    price: 50000,
    durationMinutes: 45,
    reservation: { reservationId: 71, scheduledAt: SCHEDULED_AT, status: "CONFIRMED" },
    startedAt: null,
    endedAt: null,
    chatRefId: "session-55-chat",
    ...overrides,
  };
}

function at(iso: string): Date {
  return new Date(iso);
}

describe("세션이 어느 때인지", () => {
  it("한참 전에는 들어갈 수 없다", () => {
    expect(sessionPhase(session(), at("2026-08-29T13:00:00+09:00"))).toBe("BEFORE");
  });

  it(`시작 ${SESSION_OPEN_BEFORE_MINUTES}분 전이 되면 열린다`, () => {
    // 1분 차이로 갈리는 자리라 경계를 양쪽에서 본다.
    expect(sessionPhase(session(), at("2026-08-29T13:49:00+09:00"))).toBe("BEFORE");
    expect(sessionPhase(session(), at("2026-08-29T13:50:00+09:00"))).toBe("OPEN");
  });

  it("시작 시각이 지나도 끝나기 전까지는 열려 있다", () => {
    expect(sessionPhase(session(), at("2026-08-29T14:20:00+09:00"))).toBe("OPEN");
  });

  it("끝난 세션은 시각과 상관없이 끝난 것이다", () => {
    const ended = session({
      startedAt: "2026-08-29T14:00:00+09:00",
      endedAt: "2026-08-29T14:32:00+09:00",
    });
    expect(sessionPhase(ended, at("2026-08-29T14:10:00+09:00"))).toBe("ENDED");
  });
});

describe("실제로 한 시간", () => {
  it("끝나야 잰다", () => {
    expect(sessionElapsedMinutes(session())).toBeNull();
    expect(sessionElapsedMinutes(session({ startedAt: "2026-08-29T14:00:00+09:00" }))).toBeNull();
  });

  it("하기로 한 시간이 아니라 한 시간을 돌려준다", () => {
    const ended = session({
      durationMinutes: 45,
      startedAt: "2026-08-29T14:00:00+09:00",
      endedAt: "2026-08-29T14:32:00+09:00",
    });
    expect(sessionElapsedMinutes(ended)).toBe(32);
  });
});

describe("배너를 띄울 세션", () => {
  const signature = { isSigned: true, signedAt: SCHEDULED_AT, fileId: 1, userId: 1 };

  function contract(overrides: Partial<Contract> = {}): Contract {
    return {
      contractId: 120,
      roomId: 92,
      contractType: "CONSULTING",
      status: "SIGNED",
      consulting: {
        status: "SIGNED",
        totalAmount: 50000,
        topic: "회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다",
        sessions: [session()],
      },
      development: null,
      detail: null,
      clientSignature: signature,
      developerSignature: signature,
      messages: [],
      contractText: null,
      updatedAt: SCHEDULED_AT,
      ...overrides,
    } as Contract;
  }

  it("예약이 확정된 컨설팅 계약에만 세션이 있다", () => {
    expect(confirmedSession(contract())?.sessionId).toBe(55);
  });

  it("협의 중인 일시는 아직 약속이 아니다", () => {
    const proposed = contract({
      consulting: {
        status: "SIGNED",
        totalAmount: 50000,
        topic: "회원탈퇴 경로와 결제 간헐 실패를 함께 봅니다",
        sessions: [
          session({ reservation: { reservationId: 71, scheduledAt: SCHEDULED_AT, status: "PROPOSED" } }),
        ],
      },
    });
    expect(confirmedSession(proposed)).toBeNull();
  });

  it("계약서가 없거나 개발 계약이면 세션이 없다", () => {
    expect(confirmedSession(null)).toBeNull();
    expect(confirmedSession(contract({ contractType: "DEVELOPMENT", consulting: null }))).toBeNull();
  });
});
