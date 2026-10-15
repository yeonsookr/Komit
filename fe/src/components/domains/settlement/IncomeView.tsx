import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { Card, Money } from "@/components/common";
import { ROUTES } from "@/lib/routes";
import { groupByContract } from "@/lib/settlement";
import type { IncomeResponse } from "@/types/settlement";
import { ContractGroup } from "./ContractGroup";

export interface IncomeViewProps {
  income: IncomeResponse;
}

/**
 * IncomeView — 「7_개발자_소득내역」. 계약이 끝나면 받을 돈.
 *
 * 달로 자르지 않습니다. 목 명세에 달 파라미터가 없고, 받을 돈은 한 달의 집계가 아니라
 * 계약이 끝날 때 한 번에 정해지는 값이기 때문입니다. (`R-PAY-01` 일괄 지급)
 *
 * 목 명세의 이 응답에는 프로젝트 이름이 없어 묶음 머리를 상대 이름으로 적습니다.
 */
export function IncomeView({ income }: Readonly<IncomeViewProps>) {
  const groups = groupByContract(income.items);
  const account = income.settlementAccount;

  return (
    <>
      <Card variant="surface" padding="lg">
        <p className="text-[13px] text-fg-muted">계약이 끝나면 받을 돈</p>
        <p className="mt-2 flex items-baseline gap-1.5">
          <Money value={income.expectedAmount} unit="" tone="info" className="text-4xl" />
          <span className="text-base font-semibold text-fg-muted">원</span>
        </p>
        {/*
          R-PAY-01 전 작업 통과 뒤 한 번에 지급 · R-PAY-02 통과 확정 후 3영업일 이내.
          값을 여기 적지 않고 규칙 번호로 인용합니다 — 기간이 바뀌면 RULES.md 를 고칩니다.
        */}
        <p className="mt-4 border-t border-line pt-4 text-[13px] text-fg-muted">
          {income.awaitingCount > 0
            ? `확인이 남은 ${income.awaitingCount}건을 모두 통과한 뒤 한 번에 들어갑니다.`
            : "계약이 끝나면 한 번에 들어갑니다."}
        </p>
      </Card>

      <section aria-labelledby="income-breakdown" className="mt-10">
        <div className="flex items-baseline justify-between gap-4">
          <h2 id="income-breakdown" className="text-lg font-bold text-fg">
            계산
          </h2>
          <p className="text-[13px] text-fg-muted">계약 완료 시 기준</p>
        </div>

        <dl className="mt-4 flex flex-col gap-3 border-t border-line-subtle pt-4 text-sm">
          <div className="flex items-center justify-between">
            <dt className="text-fg-secondary">확인이 남은 작업 · {income.awaitingCount}건</dt>
            <dd>
              <Money value={income.awaitingAmount} className="text-sm" />
            </dd>
          </div>
          <div className="flex items-center justify-between border-t border-line pt-3">
            <dt className="text-base font-bold text-fg">지급액</dt>
            <dd>
              <Money value={income.expectedAmount} tone="info" className="text-lg" />
            </dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby="income-items" className="mt-10">
        <h2 id="income-items" className="text-lg font-bold text-fg">
          거래
        </h2>

        {groups.length === 0 ? (
          <p className="mt-5 rounded-xl border border-line px-6 py-10 text-center text-sm text-fg-muted">
            아직 받을 돈이 없습니다.
          </p>
        ) : (
          <div className="mt-5 flex flex-col gap-4">
            {groups.map((group) => (
              <ContractGroup
                key={group.contractId}
                title={group.counterpartName}
                items={group.items}
                subtotal={group.subtotal}
              />
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="income-account" className="mt-10">
        <div className="flex items-baseline justify-between gap-4">
          <h2 id="income-account" className="text-lg font-bold text-fg">
            정산 계좌
          </h2>
          {account && <p className="text-[13px] text-fg-muted">{account.accountHolder}</p>}
        </div>

        {/*
          계좌가 없으면 등록 안내로 바뀝니다. 같은 API 이고 화면만 갈립니다(목 명세).
          등록·변경은 이 화면에 없고 마이페이지에 있습니다 — 정산에는 쓰기 API 가 없습니다.
        */}
        <Card padding="lg" className="mt-4 flex flex-wrap items-center justify-between gap-4">
          {account ? (
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-fg">
                {account.bankName} {account.maskedNumber}
              </p>
              <p className="mt-0.5 text-xs text-fg-subtle">예금주 {account.accountHolder}</p>
            </div>
          ) : (
            <div className="min-w-0">
              <p className="text-sm font-semibold text-fg">등록된 정산 계좌가 없습니다</p>
              <p className="mt-0.5 text-xs text-fg-subtle">
                계좌를 등록해야 대금을 받을 수 있습니다.
              </p>
            </div>
          )}

          <Link href={ROUTES.mypage} className={buttonClass({ variant: "outline", size: "md" })}>
            마이페이지에서 {account ? "변경" : "등록"}
          </Link>
        </Card>
      </section>
    </>
  );
}
