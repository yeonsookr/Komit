import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

import {
  PAYOUT_REFRESH_INTERVAL_MS,
  PAYOUT_REFRESH_MIN_GAP_MS,
  PayoutRefresh,
  shouldRefreshPayout,
} from "./PayoutRefresh";

const base = { active: true, visible: true, lastRefreshAt: 1_000_000 };

describe("shouldRefreshPayout", () => {
  it("켜져 있고 창이 보이면 주기가 돌아왔을 때 다시 읽는다", () => {
    expect(shouldRefreshPayout({ ...base, now: base.lastRefreshAt + PAYOUT_REFRESH_INTERVAL_MS })).toBe(true);
  });

  it("꺼져 있으면 읽지 않는다", () => {
    expect(
      shouldRefreshPayout({ ...base, active: false, now: base.lastRefreshAt + PAYOUT_REFRESH_INTERVAL_MS }),
    ).toBe(false);
  });

  it("숨은 탭에서는 읽지 않는다", () => {
    expect(
      shouldRefreshPayout({ ...base, visible: false, now: base.lastRefreshAt + PAYOUT_REFRESH_INTERVAL_MS }),
    ).toBe(false);
  });

  it("창으로 돌아와 이벤트가 겹쳐 와도 방금 읽었으면 한 번만 읽는다", () => {
    expect(shouldRefreshPayout({ ...base, now: base.lastRefreshAt + PAYOUT_REFRESH_MIN_GAP_MS - 1 })).toBe(false);
    expect(shouldRefreshPayout({ ...base, now: base.lastRefreshAt + PAYOUT_REFRESH_MIN_GAP_MS })).toBe(true);
  });

  it("주기는 겹침 간격보다 길다", () => {
    expect(PAYOUT_REFRESH_INTERVAL_MS).toBeGreaterThan(PAYOUT_REFRESH_MIN_GAP_MS);
  });
});

describe("PayoutRefresh", () => {
  it("화면에 아무것도 그리지 않는다", () => {
    expect(renderToStaticMarkup(<PayoutRefresh active />)).toBe("");
    expect(renderToStaticMarkup(<PayoutRefresh active={false} />)).toBe("");
  });
});
