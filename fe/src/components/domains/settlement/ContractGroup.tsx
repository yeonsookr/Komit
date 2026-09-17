import { Money } from "@/components/common";
import { countByState } from "@/lib/settlement";
import type { MoneyFlowItem } from "@/types/settlement";
import { MoneyFlowRow } from "./MoneyFlowRow";

export interface ContractGroupProps {
  /** 묶음 머리에 적는 이름. */
  title: string;
  items: MoneyFlowItem[];
  subtotal: number;
}

/**
 * ContractGroup — 계약 하나의 거래를 접었다 펼 수 있게 묶습니다.
 *
 * `details` 를 쓰는 이유는 스크립트 없이도 접히고 펴지기 때문입니다. 상태를 들고
 * 클라이언트 컴포넌트가 될 이유가 없습니다. 기본은 펼친 상태입니다.
 */
export function ContractGroup({ title, items, subtotal }: Readonly<ContractGroupProps>) {
  const counts = countByState(items);

  return (
    <details open className="group overflow-hidden rounded-xl border border-line">
      {/* `list-none` 과 웹킷 표식 숨김 — 기본 삼각형을 지우고 방향이 바뀌는 표식을 직접 답니다. */}
      <summary className="flex cursor-pointer list-none items-center gap-3 bg-surface px-4 py-3 [&::-webkit-details-marker]:hidden">
        <span className="min-w-0 flex-1 truncate text-sm font-bold text-fg">{title}</span>
        <span className="shrink-0 text-xs text-fg-subtle">
          {counts.map(({ label, count }) => `${label} ${count}건`).join(" · ")}
        </span>
        <Money value={subtotal} className="shrink-0 text-sm" />
        <span
          aria-hidden
          className="shrink-0 text-fg-subtle transition-transform group-open:rotate-180"
        >
          ▾
        </span>
      </summary>

      <ul className="divide-y divide-line-subtle">
        {items.map((item) => (
          <MoneyFlowRow
            key={`${item.contractId}-${item.title}-${item.occurredAt}`}
            item={item}
            showCounterpart={false}
          />
        ))}
      </ul>
    </details>
  );
}
