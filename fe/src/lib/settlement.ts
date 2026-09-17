import type { BadgeTone } from "@/components/common";
import type { MoneyFlowItem, MoneyFlowState } from "@/types/settlement";

/**
 * 정산 화면의 순수 계산. 컴포넌트에 규칙을 쌓지 않습니다.
 *
 * 금액은 서버가 준 합계를 그대로 씁니다. 여기서 더하는 것은 목록을 접어 보여주는
 * 소계뿐이고, 화면에 크게 찍히는 총액을 프런트에서 다시 계산하지 않습니다.
 */

/**
 * 조회할 달을 넘기는 query 이름. 읽는 쪽인 여기가 정하고 보내는 쪽이 가져다 씁니다
 * (docs/FRONTEND_CONVENTIONS.md 8.2). 이름은 목 명세의 파라미터와 같습니다.
 */
export const MONTH_PARAM = "month";

/** `YYYY-MM` 인지 봅니다. 아니면 서버에 넘기지 않고 이번 달로 둡니다. */
export function isMonthKey(value: unknown): value is string {
  return typeof value === "string" && /^\d{4}-(0[1-9]|1[0-2])$/.test(value);
}

/** 주소에서 읽은 값을 달로 씁니다. 형식이 아니면 undefined — 서버가 이번 달로 봅니다. */
export function readMonthParam(value: string | string[] | undefined): string | undefined {
  const first = Array.isArray(value) ? value[0] : value;
  return isMonthKey(first) ? first : undefined;
}

/**
 * 지금이 몇 월인지. 주소에 달이 없을 때 화면에 적을 값입니다.
 *
 * 타임존을 서울로 고정합니다(`lib/format.ts` 와 같은 이유). 이 값은 **서버에서만**
 * 계산해 props 로 내려보냅니다 — 클라이언트가 다시 계산하면 자정 언저리에 서버와
 * 다른 달이 나와 hydration 이 어긋납니다.
 *
 * `now` 를 받는 것은 테스트에서 시점을 고정하기 위해서입니다.
 */
export function currentMonthKey(now: Date = new Date()): string {
  const [year, month] = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
  })
    .format(now)
    .split("-");
  return `${year}-${month}`;
}

/** "2026-08" → "2026년 8월". */
export function formatMonthLabel(month: string): string {
  const [year, rawMonth] = month.split("-");
  return `${year}년 ${Number(rawMonth)}월`;
}

/**
 * 이웃한 달. `offset` 이 -1 이면 지난달, 1 이면 다음 달입니다.
 * `Date` 를 쓰지 않고 숫자로 계산합니다 — 타임존에 따라 달이 밀리지 않게 합니다.
 */
export function shiftMonth(month: string, offset: number): string {
  const [year, rawMonth] = month.split("-").map(Number);
  const zeroBased = year * 12 + (rawMonth - 1) + offset;
  const nextYear = Math.floor(zeroBased / 12);
  const nextMonth = zeroBased - nextYear * 12 + 1;
  return `${nextYear}-${String(nextMonth).padStart(2, "0")}`;
}

/**
 * 상태 표기. 목 명세가 상태를 문자열로 두어 서버가 값을 더할 수 있으므로,
 * 모르는 값이 와도 화면이 비지 않게 값을 그대로 보여주고 중립색으로 그립니다.
 */
const STATE_LABELS: Record<MoneyFlowState, { label: string; tone: BadgeTone }> = {
  DEPOSITED: { label: "예치됨", tone: "neutral" },
  CONFIRMED: { label: "통과", tone: "success" },
  AWAITING: { label: "확인 대기", tone: "warning" },
  PAID: { label: "지급 완료", tone: "success" },
  REFUNDED: { label: "환불됨", tone: "danger" },
};

export function stateLabel(state: string): { label: string; tone: BadgeTone } {
  return STATE_LABELS[state as MoneyFlowState] ?? { label: state, tone: "neutral" };
}

/** 계약 하나로 묶은 거래. 소비·소득 목록을 접어 보여줄 때 씁니다. */
export interface ContractGroup {
  contractId: number;
  /** 묶음 머리에 적는 이름. 목 명세가 이 목록에 프로젝트 이름을 주지 않아 상대 이름을 씁니다. */
  counterpartName: string;
  items: MoneyFlowItem[];
  /** 묶음 소계. 환불이 섞이면 음수가 상쇄됩니다. */
  subtotal: number;
}

/**
 * 거래를 계약별로 묶습니다. 들어온 순서를 유지해 서버가 정한 정렬을 흔들지 않습니다.
 *
 * 소비·소득 응답에는 프로젝트 이름이 없어 묶음 이름을 상대 이름으로 적습니다.
 * 예치금 응답에는 `projectName` 이 있어 그쪽은 이 함수를 쓰지 않습니다.
 */
export function groupByContract(items: MoneyFlowItem[]): ContractGroup[] {
  const groups = new Map<number, ContractGroup>();

  for (const item of items) {
    const group = groups.get(item.contractId);
    if (group) {
      group.items.push(item);
      group.subtotal += item.amount;
      continue;
    }
    groups.set(item.contractId, {
      contractId: item.contractId,
      counterpartName: item.counterpartName,
      items: [item],
      subtotal: item.amount,
    });
  }

  return [...groups.values()];
}

/** 거래 금액의 합. 묶음 머리에 적는 소계입니다. */
export function sumAmount(items: MoneyFlowItem[]): number {
  return items.reduce((total, item) => total + item.amount, 0);
}

/** 나간 돈과 되돌아온 돈. 합계 아래 "700,000원 - 환불 30,000원" 한 줄에 씁니다. */
export interface SpendingBreakdown {
  /** 나간 돈의 합(양수). */
  charged: number;
  /** 되돌아온 돈의 합(양수로 바꿔 담습니다). */
  refunded: number;
}

export function spendingBreakdown(items: MoneyFlowItem[]): SpendingBreakdown {
  return items.reduce<SpendingBreakdown>(
    (acc, item) =>
      item.amount < 0
        ? { ...acc, refunded: acc.refunded - item.amount }
        : { ...acc, charged: acc.charged + item.amount },
    { charged: 0, refunded: 0 },
  );
}

/** 상태별 건수. "통과 1건 · 확인 대기 2건" 처럼 묶음 머리에 적습니다. */
export function countByState(items: MoneyFlowItem[]): { label: string; count: number }[] {
  const counts = new Map<string, number>();
  for (const item of items) {
    counts.set(item.state, (counts.get(item.state) ?? 0) + 1);
  }
  return [...counts.entries()].map(([state, count]) => ({
    label: stateLabel(state).label,
    count,
  }));
}
