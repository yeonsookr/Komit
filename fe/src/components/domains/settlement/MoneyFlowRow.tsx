import { Money, StatusDot } from "@/components/common";
import { formatDateLabel } from "@/lib/format";
import { stateLabel } from "@/lib/settlement";
import type { MoneyFlowItem } from "@/types/settlement";

export interface MoneyFlowRowProps {
  item: MoneyFlowItem;
  /** 상대 이름을 적을지. 묶음 머리에 이미 적혀 있으면 끕니다. */
  showCounterpart?: boolean;
}

/**
 * MoneyFlowRow — 돈이 한 번 움직인 기록 한 줄.
 * 소비·소득·예치금이 같은 응답 모양을 써서 줄도 하나로 씁니다.
 */
export function MoneyFlowRow({ item, showCounterpart = true }: Readonly<MoneyFlowRowProps>) {
  const state = stateLabel(item.state);

  return (
    <li className="flex items-center gap-3 px-4 py-3.5">
      <StatusDot tone={state.tone} />

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-fg">{item.title}</p>
        <p className="mt-0.5 truncate text-xs text-fg-subtle">
          {showCounterpart && `${item.counterpartName} · `}
          {formatDateLabel(item.occurredAt)}
        </p>
      </div>

      <span className="shrink-0 text-xs text-fg-muted">{state.label}</span>
      <Money value={item.amount} signed={item.amount < 0} className="shrink-0 text-sm" />
    </li>
  );
}
