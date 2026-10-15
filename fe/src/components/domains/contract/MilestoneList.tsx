import { Card, Mono, Money } from "@/components/common";
import { isHandover } from "@/lib/contract";
import type { Milestone } from "@/types/contract";

export interface MilestoneListProps {
  milestones: Milestone[];
  /**
   * 작업별 기간을 보여줄지. 기간은 최종 납기일이 어떻게 나왔는지 보여주는
   * 참고값이라 계약 전에만 보여줍니다.
   */
  showPeriod?: boolean;
}

/**
 * MilestoneList — 작업과 확인 문장을 읽기 전용으로 늘어놓습니다.
 *
 * 계약서 상세·검토·예치·진행이 같은 모양을 씁니다. 카드를 작업마다 나누지 않고
 * 한 장 안에서 구분선으로 가릅니다 — 화면이 그렇게 그려져 있습니다.
 *
 * 인계 작업은 가격이 0 이라 금액을 보여주지 않습니다.
 */
export function MilestoneList({
  milestones,
  showPeriod,
}: Readonly<MilestoneListProps>) {
  return (
    <Card padding="none">
      <ul className="divide-y divide-line-subtle">
        {milestones.map((milestone) => {
          return (
            <li key={milestone.milestoneId} className="px-4 py-4 sm:px-5">
              <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <p className="min-w-0 flex-1 text-sm font-semibold text-fg">
                  {milestone.title}
                </p>
                {!isHandover(milestone) && (
                  <Money
                    value={milestone.price}
                    className="text-sm text-fg-secondary"
                  />
                )}
                {showPeriod && milestone.workDate !== null && (
                  <span className="font-mono text-[13px] text-fg-subtle tabular-nums">
                    {milestone.workDate}일
                  </span>
                )}
              </div>

              {milestone.location && (
                <Mono className="mt-1 block text-xs text-fg-subtle">
                  {milestone.location}
                </Mono>
              )}

              {milestone.qaCriteria.length > 0 && (
                <ul className="mt-3 flex flex-col gap-1">
                  {milestone.qaCriteria.map((qa) => (
                    <li
                      key={qa.qaCriterionId}
                      className="text-[13px] leading-6 text-fg-muted before:mr-1.5 before:text-fg-subtle before:content-['·']"
                    >
                      {qa.testCase}
                    </li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ul>
    </Card>
  );
}
