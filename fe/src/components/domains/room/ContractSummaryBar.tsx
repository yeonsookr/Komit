import { Card, Money } from "@/components/common";
import { formatShortDateTime } from "@/lib/format";
import type { Contract } from "@/types/contract";

/** 갈래별 줄 이름. 화면이 부르는 말을 그대로 씁니다. */
const CONTRACT_LABEL = {
  CONSULTING: "컨설팅 계약서",
  DEVELOPMENT: "개발 계약서",
} as const;

export interface ContractSummaryBarProps {
  /** 아직 쓰지 않았으면 null. 그때는 금액 자리에 '없음' 이 들어갑니다. */
  contract: Contract | null;
}

/**
 * ContractSummaryBar — 방 머리에 붙는 계약서 요약 줄.
 *   개발 계약서 | 650,000원 | 8/23 10:20 갱신됨
 *
 * 요청서 줄과 짝을 이룹니다. 계약 금액은 갈래에 따라 `development` 또는
 * `consulting` 한쪽에만 있습니다.
 */
export function ContractSummaryBar({ contract }: Readonly<ContractSummaryBarProps>) {
  const amount = contract?.development?.totalAmount ?? contract?.consulting?.totalAmount ?? null;

  return (
    <Card variant="surface" padding="none">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3.5 sm:px-5">
        <span className="text-sm font-semibold text-fg">
          {contract ? CONTRACT_LABEL[contract.contractType] : "계약서"}
        </span>

        {amount === null ? (
          <span className="text-sm text-fg-muted">없음</span>
        ) : (
          <Money value={amount} className="text-sm text-fg-secondary" />
        )}

        <span className="ml-auto font-mono text-[13px] text-fg-subtle tabular-nums">
          {contract ? `${formatShortDateTime(contract.updatedAt)} 갱신됨` : "—"}
        </span>
      </div>
    </Card>
  );
}
