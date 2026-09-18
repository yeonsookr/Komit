import Link from "next/link";
import { cn } from "@/lib/cn";
import { ADMIN_REPORT_STATUS_LABEL } from "@/lib/admin/labels";
import { reportsHref } from "@/lib/admin/reportQuery";
import type { AdminReportStatus } from "@/types/admin";

export interface ReportStatusFilterProps {
  /** 지금 걸린 필터. 비어 있으면 「전체」입니다. */
  status?: AdminReportStatus;
  /** 필터와 무관한 전체 수. 서버가 목록과 함께 줍니다. */
  openCount: number;
  resolvedCount: number;
}

/**
 * 신고 목록의 상태 필터.
 *
 * **버튼이 아니라 링크입니다.** 필터가 주소에 남아야 새로고침·뒤로 가기·주소 공유가
 * 그대로 동작하고, 목록을 서버에서 걸러 오므로 화면이 눌린 뒤에야 움직일 이유가 없습니다.
 * 그래서 `SegmentedTabs`(클라이언트 상태를 다루는 공용 탭)를 쓰지 않고 같은 모양의
 * 링크로 그립니다 — 회색 트랙 + 흰 pill 은 이 프로젝트의 유일한 탭 모양입니다.
 *
 * 옆에 붙는 숫자는 **필터와 무관한 전체 수**입니다. 「접수됨」을 보는 중에도 종결된
 * 건이 몇 개인지 알 수 있어야 합니다.
 */
export function ReportStatusFilter({
  status,
  openCount,
  resolvedCount,
}: Readonly<ReportStatusFilterProps>) {
  const items: { value?: AdminReportStatus; label: string; count?: number }[] = [
    { value: undefined, label: "전체", count: openCount + resolvedCount },
    { value: "OPEN", label: ADMIN_REPORT_STATUS_LABEL.OPEN, count: openCount },
    { value: "RESOLVED", label: ADMIN_REPORT_STATUS_LABEL.RESOLVED, count: resolvedCount },
  ];

  return (
    <nav
      aria-label="신고 상태 필터"
      className="inline-flex max-w-full items-center gap-0.5 overflow-x-auto rounded-lg bg-surface p-1"
    >
      {items.map((item) => {
        const active = item.value === status;
        return (
          <Link
            key={item.label}
            href={reportsHref({ status: item.value })}
            aria-current={active ? "page" : undefined}
            className={cn(
              "inline-flex h-8 items-center justify-center gap-1.5 rounded-md px-3.5 text-sm font-semibold whitespace-nowrap transition-colors",
              active ? "bg-bg text-fg shadow-sm" : "text-fg-subtle hover:text-fg",
            )}
          >
            {item.label}
            {item.count !== undefined && (
              <span
                className={cn(
                  "font-mono text-xs tabular-nums",
                  active ? "text-primary" : "text-fg-subtle",
                )}
              >
                {item.count}
              </span>
            )}
          </Link>
        );
      })}
    </nav>
  );
}
