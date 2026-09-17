import { Card, Money } from "@/components/common";
import { sumAmount } from "@/lib/settlement";
import type { DepositsResponse } from "@/types/settlement";
import { ContractGroup } from "./ContractGroup";

export interface DepositsViewProps {
  deposits: DepositsResponse;
}

/**
 * DepositsView — 「7_의뢰인_예치금」. 맡긴 돈이 어디에 걸려 있는지.
 *
 * 달로 자르지 않습니다. 목 명세에 달 파라미터가 없고, 예치금은 한 달의 집계가 아니라
 * 지금의 잔액이기 때문입니다.
 *
 * 이 응답에는 `projectName` 이 있어 묶음 머리에 프로젝트와 상대를 함께 적습니다.
 * 소비·소득 목록에는 그 필드가 없어 그쪽은 상대 이름만 적습니다.
 */
export function DepositsView({ deposits }: Readonly<DepositsViewProps>) {
  return (
    <>
      <Card variant="surface" padding="lg">
        <p className="text-[13px] text-fg-muted">예치중인 돈</p>
        <p className="mt-2 flex items-baseline gap-1.5">
          <Money value={deposits.totalRemaining} unit="" className="text-4xl" />
          <span className="text-base font-semibold text-fg-muted">원</span>
        </p>
        {/* R-PAY-03 통과해도 계약이 끝날 때까지 남음 · R-PAY-04 중도 종료 시 통과분만 지급하고 나머지 환불 */}
        <div className="mt-4 flex flex-col gap-1 border-t border-line pt-4 text-[13px] text-fg-muted">
          <p>통과시킨 작업의 대금도 계약이 전부 끝날 때까지 예치금에 남습니다.</p>
          <p>중간에 끝나면 통과된 작업분만 지급되고 나머지는 결제하신 수단으로 환불됩니다.</p>
        </div>
      </Card>

      {deposits.contracts.length === 0 ? (
        <p className="mt-10 rounded-xl border border-line px-6 py-10 text-center text-sm text-fg-muted">
          맡긴 돈이 없습니다.
        </p>
      ) : (
        deposits.contracts.map((contract) => (
          <section
            key={contract.contractId}
            aria-labelledby={`deposit-${contract.contractId}`}
            className="mt-10"
          >
            <div className="flex items-baseline justify-between gap-4">
              <h2 id={`deposit-${contract.contractId}`} className="text-lg font-bold text-fg">
                {contract.projectName}
              </h2>
              <p className="text-[13px] text-fg-muted">{contract.counterpartName}</p>
            </div>

            <dl className="mt-4 flex flex-col gap-3 border-t border-line-subtle pt-4 text-sm">
              <div className="flex items-center justify-between">
                <dt className="text-fg-secondary">예치한 돈</dt>
                <dd>
                  <Money value={contract.depositedAmount} className="text-sm" />
                </dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-fg-secondary">지급된 돈</dt>
                <dd>
                  <Money value={contract.releasedAmount} className="text-sm" />
                </dd>
              </div>
              <div className="flex items-center justify-between border-t border-line pt-3">
                <dt className="text-base font-bold text-fg">남은 예치금</dt>
                <dd>
                  <Money value={contract.remainingAmount} className="text-lg" />
                </dd>
              </div>
            </dl>

            <div className="mt-6">
              {/*
                묶음 머리의 소계는 목록에 있는 작업 금액의 합입니다. 위의 「남은 예치금」과
                같아 보일 수 있지만 서로 다른 값이라 계약의 잔액을 여기 옮겨 적지 않습니다.
              */}
              <ContractGroup
                title={`${contract.projectName} · ${contract.counterpartName}`}
                items={contract.items}
                subtotal={sumAmount(contract.items)}
              />
            </div>
          </section>
        ))
      )}
    </>
  );
}
