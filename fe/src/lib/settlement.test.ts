import { describe, expect, it } from "vitest";
import {
  countByState,
  currentMonthKey,
  formatMonthLabel,
  groupByContract,
  isMonthKey,
  readMonthParam,
  shiftMonth,
  spendingBreakdown,
  stateLabel,
  sumAmount,
} from "./settlement";
import type { MoneyFlowItem } from "@/types/settlement";

function item(partial: Partial<MoneyFlowItem> = {}): MoneyFlowItem {
  return {
    contractId: 130,
    title: "개발 대금",
    counterpartName: "이준",
    amount: 650000,
    occurredAt: "2026-08-23T09:40:00+09:00",
    state: "DEPOSITED",
    ...partial,
  };
}

describe("isMonthKey", () => {
  it("YYYY-MM 만 받는다", () => {
    expect(isMonthKey("2026-08")).toBe(true);
    expect(isMonthKey("2026-12")).toBe(true);
  });

  it("달이 범위를 벗어나거나 형식이 다르면 거른다", () => {
    for (const bad of ["2026-00", "2026-13", "2026-8", "26-08", "2026/08", "", null, 202608]) {
      expect(isMonthKey(bad)).toBe(false);
    }
  });
});

describe("readMonthParam", () => {
  it("형식이 맞으면 그대로 쓴다", () => {
    expect(readMonthParam("2026-08")).toBe("2026-08");
  });

  it("같은 이름이 여러 번 오면 첫 값을 본다", () => {
    expect(readMonthParam(["2026-08", "2026-09"])).toBe("2026-08");
  });

  it("없거나 형식이 아니면 undefined — 서버가 이번 달로 본다", () => {
    expect(readMonthParam(undefined)).toBeUndefined();
    expect(readMonthParam("지난달")).toBeUndefined();
  });
});

describe("formatMonthLabel", () => {
  it("앞의 0 을 떼고 적는다", () => {
    expect(formatMonthLabel("2026-08")).toBe("2026년 8월");
    expect(formatMonthLabel("2026-11")).toBe("2026년 11월");
  });
});

describe("shiftMonth", () => {
  it("한 달 앞뒤로 옮긴다", () => {
    expect(shiftMonth("2026-08", -1)).toBe("2026-07");
    expect(shiftMonth("2026-08", 1)).toBe("2026-09");
  });

  it("해를 넘어간다", () => {
    expect(shiftMonth("2026-01", -1)).toBe("2025-12");
    expect(shiftMonth("2026-12", 1)).toBe("2027-01");
  });

  it("여러 달을 한 번에 옮긴다", () => {
    expect(shiftMonth("2026-08", -12)).toBe("2025-08");
    expect(shiftMonth("2026-08", 5)).toBe("2027-01");
  });
});

describe("stateLabel", () => {
  it("명세의 다섯 상태를 우리말로 바꾼다", () => {
    expect(stateLabel("CONFIRMED")).toEqual({ label: "통과", tone: "success" });
    expect(stateLabel("AWAITING")).toEqual({ label: "확인 대기", tone: "warning" });
    expect(stateLabel("REFUNDED")).toEqual({ label: "환불됨", tone: "danger" });
  });

  it("모르는 상태가 와도 비우지 않고 값을 그대로 보여준다", () => {
    expect(stateLabel("SOMETHING_NEW")).toEqual({ label: "SOMETHING_NEW", tone: "neutral" });
  });
});

describe("groupByContract", () => {
  it("계약별로 묶고 소계를 낸다", () => {
    const groups = groupByContract([
      item({ contractId: 130, amount: 400000 }),
      item({ contractId: 120, amount: 70000, counterpartName: "김미미" }),
      item({ contractId: 130, amount: 200000 }),
    ]);

    expect(groups).toHaveLength(2);
    expect(groups[0]).toMatchObject({ contractId: 130, subtotal: 600000 });
    expect(groups[0].items).toHaveLength(2);
    expect(groups[1]).toMatchObject({ contractId: 120, counterpartName: "김미미", subtotal: 70000 });
  });

  it("들어온 순서를 유지한다 — 서버가 정한 정렬을 흔들지 않는다", () => {
    const groups = groupByContract([
      item({ contractId: 9 }),
      item({ contractId: 3 }),
      item({ contractId: 9 }),
    ]);
    expect(groups.map((group) => group.contractId)).toEqual([9, 3]);
  });

  it("환불이 섞이면 소계에서 상쇄된다", () => {
    const [group] = groupByContract([
      item({ amount: 50000 }),
      item({ amount: -30000, state: "REFUNDED" }),
    ]);
    expect(group.subtotal).toBe(20000);
  });

  it("빈 목록은 빈 배열", () => {
    expect(groupByContract([])).toEqual([]);
  });
});

describe("currentMonthKey", () => {
  it("서울 기준으로 달을 낸다", () => {
    expect(currentMonthKey(new Date("2026-08-15T12:00:00+09:00"))).toBe("2026-08");
  });

  it("UTC 로는 전날이어도 서울 기준 달을 따른다", () => {
    // 2026-08-31 23:00 KST = 2026-08-31 14:00 UTC — 아직 8월입니다.
    expect(currentMonthKey(new Date("2026-08-31T23:00:00+09:00"))).toBe("2026-08");
    // 2026-09-01 00:30 KST = 2026-08-31 15:30 UTC — 서울은 이미 9월입니다.
    expect(currentMonthKey(new Date("2026-09-01T00:30:00+09:00"))).toBe("2026-09");
  });
});

describe("sumAmount", () => {
  it("환불을 포함해 더한다", () => {
    expect(sumAmount([item({ amount: 400000 }), item({ amount: -30000 })])).toBe(370000);
  });

  it("빈 목록은 0", () => {
    expect(sumAmount([])).toBe(0);
  });
});

describe("spendingBreakdown", () => {
  it("나간 돈과 환불을 나눠 센다", () => {
    expect(
      spendingBreakdown([
        item({ amount: 650000 }),
        item({ amount: 50000 }),
        item({ amount: -30000, state: "REFUNDED" }),
      ]),
    ).toEqual({ charged: 700000, refunded: 30000 });
  });

  it("환불이 없으면 0", () => {
    expect(spendingBreakdown([item({ amount: 1000 })])).toEqual({ charged: 1000, refunded: 0 });
  });
});

describe("countByState", () => {
  it("상태별 건수를 우리말 라벨로 센다", () => {
    expect(
      countByState([
        item({ state: "CONFIRMED" }),
        item({ state: "AWAITING" }),
        item({ state: "AWAITING" }),
      ]),
    ).toEqual([
      { label: "통과", count: 1 },
      { label: "확인 대기", count: 2 },
    ]);
  });
});
