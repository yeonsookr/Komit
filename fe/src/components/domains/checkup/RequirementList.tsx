import { Card, Mono } from "@/components/common";
import { EmptyState } from "@/components/feedback";
import type { Issue } from "@/types/diagnosis";

export interface RequirementListProps {
  requirements: Issue[];
  /** 비어 있을 때 적을 말. 보는 사람에 따라 다릅니다. */
  emptyTitle: string;
  emptyDescription: string;
}

/**
 * 요청서에 담긴 요구사항.
 *
 * 의뢰인의 요청서 화면과 개발자가 받은 요청서 화면이 같은 것을 그립니다. 두 화면이 다른 것은
 * 무엇을 할 수 있는지이지 무엇이 담겼는지가 아니므로, 목록은 한곳에 둡니다.
 */
export function RequirementList({
  requirements,
  emptyTitle,
  emptyDescription,
}: Readonly<RequirementListProps>) {
  if (requirements.length === 0) {
    return <EmptyState className="mt-4" title={emptyTitle} description={emptyDescription} />;
  }

  return (
    <Card variant="outline" padding="none" className="mt-3">
      <ol>
        {requirements.map((issue, index) => (
          <li
            key={issue.issueId}
            className="px-5 py-5 [&:not(:first-child)]:border-t [&:not(:first-child)]:border-line-subtle"
          >
            <div className="flex gap-3">
              <span className="font-mono text-[13px] font-semibold text-primary tabular-nums">
                {index + 1}
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-bold text-fg">{issue.title}</p>
                {issue.location && (
                  <Mono className="mt-1 block text-[13px] text-fg-subtle">{issue.location}</Mono>
                )}

                {/* 확인 문장은 통과 판정의 기준이 됩니다. */}
                {issue.qaCriteria.length > 0 && (
                  <div className="mt-3 rounded-lg bg-surface px-4 py-3">
                    <p className="text-xs font-semibold text-fg-subtle">확인 문장</p>
                    <ul className="mt-1.5 flex flex-col gap-1">
                      {issue.qaCriteria.map((qa) => (
                        <li key={qa.qaCriterionId} className="text-[13px] text-fg-secondary">
                          · {qa.testCase}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            </div>
          </li>
        ))}
      </ol>
    </Card>
  );
}
