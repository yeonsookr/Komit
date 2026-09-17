import { Card, Money } from "@/components/common";
import { formatDateLabel } from "@/lib/format";
import { formatMonthLabel, spendingBreakdown, stateLabel } from "@/lib/settlement";
import type { SpendingResponse } from "@/types/settlement";

export interface SpendingViewProps {
  month: string;
  spending: SpendingResponse;
}

/**
 * SpendingView — 「7_의뢰인_소비내역」. 그 달에 쓴 돈.
 *
 * 합계는 서버가 준 `monthlyTotal` 을 그대로 씁니다. 그 아래 "나간 돈 − 환불" 한 줄만
 * 목록에서 셉니다. 환불은 목록에 음수로 들어옵니다(목 명세).
 *
 * 목 명세의 이 응답에는 프로젝트 이름과 결제 수단이 없습니다. 화면 편의로 만들지 않고
 * 있는 필드(`title`·`counterpartName`)로 적습니다.
 */
export function SpendingView({ month, spending }: Readonly<SpendingViewProps>) {
  const { charged, refunded } = spendingBreakdown(spending.items);

  return (
    <>
      <Card variant="surface" padding="lg">
        <p className="text-[13px] text-fg-muted">{formatMonthLabel(month)} 합계</p>
        <p className="mt-2 flex items-baseline gap-1.5">
          <Money value={spending.monthlyTotal} unit="" tone="danger" className="text-4xl" />
          <span className="text-base font-semibold text-fg-muted">원</span>
        </p>
        {refunded > 0 && (
          <p className="mt-4 border-t border-line pt-4 text-[13px] text-fg-muted">
            나간 돈 {charged.toLocaleString("ko-KR")}원 − 환불 {refunded.toLocaleString("ko-KR")}원
          </p>
        )}
      </Card>

      <section aria-labelledby="spending-items" className="mt-10">
        <h2 id="spending-items" className="text-lg font-bold text-fg">
          거래
        </h2>

        {spending.items.length === 0 ? (
          <p className="mt-5 rounded-xl border border-line px-6 py-10 text-center text-sm text-fg-muted">
            {formatMonthLabel(month)}에 쓴 돈이 없습니다.
          </p>
        ) : (
          <ul className="mt-5 divide-y divide-line-subtle border-y border-line-subtle">
            {spending.items.map((item) => (
              <li
                key={`${item.contractId}-${item.title}-${item.occurredAt}`}
                className="flex items-center gap-4 py-4"
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold text-fg">{item.title}</p>
                  <p className="mt-0.5 truncate text-xs text-fg-subtle">
                    {item.counterpartName} · {formatDateLabel(item.occurredAt)} ·{" "}
                    {stateLabel(item.state).label}
                  </p>
                </div>
                <Money
                  value={item.amount}
                  signed={item.amount < 0}
                  tone={item.amount < 0 ? "success" : "default"}
                  className="shrink-0 text-sm"
                />
              </li>
            ))}
          </ul>
        )}

        <div className="mt-4 flex items-center justify-between">
          <span className="text-base font-bold text-fg">합계</span>
          <Money value={spending.monthlyTotal} tone="danger" className="text-lg" />
        </div>
      </section>
    </>
  );
}
