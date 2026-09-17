"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Money } from "@/components/common";
import { cn } from "@/lib/cn";
import { ROUTES } from "@/lib/routes";
import { MONTH_PARAM, formatMonthLabel, shiftMonth } from "@/lib/settlement";

export interface SettlementSidebarProps {
  /** 보고 있는 달. `YYYY-MM`. */
  month: string;
  /** 그 달에 쓴 돈. */
  spendingTotal: number;
  /** 계약이 끝나면 받을 돈. */
  incomeExpected: number;
  /** 확인이 남은 건수. 소득 줄의 설명에 씁니다. */
  incomeAwaitingCount: number;
  /** 지금 예치금에 남은 돈. */
  depositRemaining: number;
  /** 예치금에서 확인이 남은 건수. */
  depositAwaitingCount: number;
}

/**
 * SettlementSidebar — 정산 화면 왼쪽 목록.
 *
 * 한 사람이 의뢰인이면서 개발자일 수 있어 소비와 소득을 함께 둡니다. 역할로 가르지
 * 않고 둘 다 보여주고, 해당하는 거래가 없으면 금액이 0 으로 보입니다.
 *
 * 달 고르기는 「월별 집계」 에만 걸립니다. 소득과 예치금은 목 명세에 달 파라미터가
 * 없어 언제나 현재 기준입니다(`lib/api/services/settlements.ts`).
 */
export function SettlementSidebar({
  month,
  spendingTotal,
  incomeExpected,
  incomeAwaitingCount,
  depositRemaining,
  depositAwaitingCount,
}: Readonly<SettlementSidebarProps>) {
  const pathname = usePathname();

  /** 달을 바꿔도 보던 화면에 머무릅니다. */
  const monthHref = (offset: number) =>
    `${pathname}?${MONTH_PARAM}=${shiftMonth(month, offset)}`;

  return (
    <nav aria-label="정산 목록" className="flex flex-col gap-4 py-6">
      <h2 className="px-3 text-base font-bold text-fg">정산</h2>

      <section aria-labelledby="settlement-monthly" className="flex flex-col gap-2">
        <GroupTitle id="settlement-monthly">월별 집계</GroupTitle>

        <div className="flex items-center justify-between rounded-lg border border-line px-1 py-1">
          <MonthStep href={monthHref(-1)} label="지난달" glyph="‹" />
          <span className="font-mono text-[13px] font-semibold text-fg tabular-nums">
            {formatMonthLabel(month)}
          </span>
          <MonthStep href={monthHref(1)} label="다음 달" glyph="›" />
        </div>

        <SidebarLink
          href={`${ROUTES.settlements}?${MONTH_PARAM}=${month}`}
          active={pathname === ROUTES.settlements}
          title="소비 내역"
          note={formatMonthLabel(month)}
          amount={spendingTotal}
          tone="danger"
        />
        <SidebarLink
          href={`${ROUTES.settlementsIncome}?${MONTH_PARAM}=${month}`}
          active={pathname === ROUTES.settlementsIncome}
          title="소득 내역"
          note="계약 완료 후 지급"
          amount={incomeExpected}
          badge={incomeAwaitingCount > 0 ? `확인 ${incomeAwaitingCount}건 남음` : undefined}
        />
      </section>

      <section aria-labelledby="settlement-deposit" className="flex flex-col gap-2">
        <GroupTitle id="settlement-deposit">예치</GroupTitle>
        <SidebarLink
          href={`${ROUTES.settlementsDeposits}?${MONTH_PARAM}=${month}`}
          active={pathname === ROUTES.settlementsDeposits}
          title="예치금"
          note={depositAwaitingCount > 0 ? `확인 대기 ${depositAwaitingCount}건` : "확인 대기 없음"}
          amount={depositRemaining}
        />
      </section>
    </nav>
  );
}

function GroupTitle({ id, children }: Readonly<{ id: string; children: React.ReactNode }>) {
  return (
    <h3 id={id} className="rounded-md bg-surface px-3 py-2 text-[13px] font-semibold text-fg-secondary">
      {children}
    </h3>
  );
}

/** 달을 옮기는 버튼. `Link` 라 스크립트 없이도 동작하고 주소로 공유됩니다. */
function MonthStep({ href, label, glyph }: Readonly<{ href: string; label: string; glyph: string }>) {
  return (
    <Link
      href={href}
      aria-label={label}
      className="inline-flex size-7 items-center justify-center rounded-md text-fg-subtle transition-colors hover:bg-surface hover:text-fg"
    >
      <span aria-hidden>{glyph}</span>
    </Link>
  );
}

interface SidebarLinkProps {
  href: string;
  active: boolean;
  title: string;
  note: string;
  amount: number;
  /** 금액 색. 소비는 나가는 돈이라 붉게 적습니다. */
  tone?: "default" | "danger";
  /** 있으면 설명 자리에 이것을 대신 적습니다. */
  badge?: string;
}

function SidebarLink({ href, active, title, note, amount, tone = "default", badge }: Readonly<SidebarLinkProps>) {
  return (
    <Link
      href={href}
      aria-current={active ? "page" : undefined}
      className={cn(
        "flex items-start justify-between gap-3 rounded-lg border px-3 py-2.5 transition-colors",
        active
          ? "border-line border-l-2 border-l-primary bg-bg"
          : "border-transparent hover:bg-surface",
      )}
    >
      <span className="min-w-0">
        <span className="block truncate text-sm font-semibold text-fg">{title}</span>
        <span className="mt-0.5 block truncate text-xs text-fg-subtle">{badge ?? note}</span>
      </span>
      <Money value={amount} tone={tone === "danger" ? "danger" : "default"} className="text-[13px]" />
    </Link>
  );
}
