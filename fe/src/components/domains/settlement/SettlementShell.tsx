import { Container } from "@/components/layouts";
import type { DepositsResponse, IncomeResponse, SpendingResponse } from "@/types/settlement";
import { SettlementSidebar } from "./SettlementSidebar";

export interface SettlementShellProps {
  /** 보고 있는 달. `YYYY-MM`. */
  month: string;
  spending: SpendingResponse;
  income: IncomeResponse;
  deposits: DepositsResponse;
  /** 본문 제목. 화면마다 다릅니다. */
  title: string;
  children: React.ReactNode;
}

/**
 * SettlementShell — 정산 화면 셋이 공유하는 골격. 왼쪽 목록 + 본문.
 *
 * 레이아웃이 아니라 컴포넌트인 이유는 왼쪽 목록이 고른 달에 따라 달라지기 때문입니다.
 * Next 의 layout 은 `searchParams` 를 받지 못해 달을 알 수 없고, 주소의 달만 바뀌면
 * 다시 그려지지도 않습니다. 화면마다 이 골격을 부르면 목록이 언제나 지금 보는 달을
 * 가리킵니다.
 *
 * 좁은 화면에서는 목록이 본문 위로 올라가 가로로 눕습니다.
 */
export function SettlementShell({
  month,
  spending,
  income,
  deposits,
  title,
  children,
}: Readonly<SettlementShellProps>) {
  return (
    <div className="flex min-h-[calc(100dvh-var(--k-gnb-height))] flex-col lg:flex-row">
      <aside className="shrink-0 border-b border-line px-4 lg:w-sidebar lg:border-r lg:border-b-0 lg:px-5">
        <SettlementSidebar
          month={month}
          spendingTotal={spending.monthlyTotal}
          incomeExpected={income.expectedAmount}
          incomeAwaitingCount={income.awaitingCount}
          depositRemaining={deposits.totalRemaining}
          depositAwaitingCount={deposits.awaitingCount}
        />
      </aside>

      <div className="min-w-0 flex-1">
        <Container className="py-8 sm:py-10">
          <h1 className="text-2xl font-bold tracking-tight text-fg">{title}</h1>
          <div className="mt-8">{children}</div>
        </Container>
      </div>
    </div>
  );
}
