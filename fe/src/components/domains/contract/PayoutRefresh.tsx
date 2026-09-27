"use client";

import * as React from "react";
import { useRouter } from "next/navigation";

/** 지급 처리 중 화면을 다시 읽는 주기. 규칙에 정한 값이 아닙니다. */
export const PAYOUT_REFRESH_INTERVAL_MS = 30_000;

/** 창으로 돌아올 때 visibilitychange 와 focus 가 함께 와도 한 번만 읽도록 두는 간격. 규칙에 정한 값이 아닙니다. */
export const PAYOUT_REFRESH_MIN_GAP_MS = 1_000;

export interface PayoutRefreshTiming {
  active: boolean;
  /** 창이 보이는지. 숨은 탭에서는 읽지 않습니다. */
  visible: boolean;
  now: number;
  /** 마지막으로 읽은 시각. 처음 그린 시각으로 시작합니다. */
  lastRefreshAt: number;
}

/** 지금 화면을 다시 읽을지. 켜져 있고 창이 보이며 방금 읽지 않았을 때만 읽습니다. */
export function shouldRefreshPayout({ active, visible, now, lastRefreshAt }: PayoutRefreshTiming): boolean {
  if (!active || !visible) return false;
  return now - lastRefreshAt >= PAYOUT_REFRESH_MIN_GAP_MS;
}

/**
 * PayoutRefresh — 지급 처리 중 화면을 주기적으로, 창에 다시 돌아올 때 새로 읽습니다.
 *
 * 지급 완료는 계약 상태로만 알 수 있고 따로 조회하는 API 가 없습니다 (R-PAY-01). 그래서 서버 화면을
 * 다시 그려 상태가 바뀌었는지 봅니다. 그리는 것은 없습니다.
 */
export function PayoutRefresh({ active }: Readonly<{ active: boolean }>): null {
  const router = useRouter();

  React.useEffect(() => {
    if (!active) return;
    let lastRefreshAt = Date.now();

    const refresh = () => {
      const now = Date.now();
      const visible = document.visibilityState === "visible";
      if (!shouldRefreshPayout({ active, visible, now, lastRefreshAt })) return;
      lastRefreshAt = now;
      router.refresh();
    };

    const timer = window.setInterval(refresh, PAYOUT_REFRESH_INTERVAL_MS);
    document.addEventListener("visibilitychange", refresh);
    window.addEventListener("focus", refresh);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", refresh);
      window.removeEventListener("focus", refresh);
    };
  }, [active, router]);

  return null;
}
