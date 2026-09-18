import Link from "next/link";
import { Badge, SectionCard } from "@/components/common";
import { ROUTES } from "@/lib/routes";
import type { WithdrawalBlocker } from "@/types/mypage";

/**
 * 탈퇴를 막을 수 있는 것 세 가지.
 *
 * 서버는 막고 있는 것만 내려주지만 화면은 세 줄을 늘 보여줍니다. 무엇을 확인했는지
 * 알 수 있어야 「없음」도 정보가 됩니다. (R-ACC-05)
 */
const CATEGORIES = [
  { type: "CONTRACT", label: "진행 중인 의뢰" },
  { type: "DEPOSIT", label: "남은 예치금" },
  { type: "INCOME", label: "못 받은 소득" },
] as const;

const rowClass =
  "flex items-center gap-3 py-3.5 text-sm [&:not(:last-child)]:border-b [&:not(:last-child)]:border-line-subtle";

export interface WithdrawalBlockersProps {
  blockers: WithdrawalBlocker[];
}

/**
 * WithdrawalBlockers — 「정리해야 할 것」 칸.
 *
 * 막고 있는 줄은 그대로 보여주고 갈 곳이 있으면 「이동」을 답니다. 막는 것이 없는
 * 갈래는 회색 「없음」 한 줄로 둡니다.
 *
 * 「이동」은 계약서 화면으로 갑니다. 세 갈래 모두 정리하려면 결국 그 계약을 끝내야
 * 하므로 예치금과 소득도 같은 곳으로 보냅니다. 계약서는 방 안의 탭이라 주소가 방
 * 기준이고, 응답이 계약과 함께 `roomId` 를 주므로 계약을 다시 읽지 않습니다.
 */
export function WithdrawalBlockers({ blockers }: Readonly<WithdrawalBlockersProps>) {
  return (
    <SectionCard title="정리해야 할 것" note={`${blockers.length}건`}>
      {CATEGORIES.map((category) => {
        const found = blockers.filter((blocker) => blocker.type === category.type);

        if (found.length === 0) {
          return (
            <div key={category.type} className={rowClass}>
              <span className="min-w-0 flex-1 text-fg-subtle">{category.label}</span>
              <Badge tone="neutral" size="sm">
                없음
              </Badge>
            </div>
          );
        }

        return found.map((blocker) => (
          <div key={`${blocker.type}-${blocker.title}`} className={rowClass}>
            <span className="min-w-0 flex-1 text-fg">{blocker.title}</span>
            <Badge tone="danger" size="sm">
              진행중
            </Badge>
            {blocker.roomId !== null && (
              <Link
                href={ROUTES.roomContract(blocker.roomId)}
                className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                이동 ›
              </Link>
            )}
          </div>
        ));
      })}
    </SectionCard>
  );
}
