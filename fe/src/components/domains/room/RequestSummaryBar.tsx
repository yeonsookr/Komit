import Link from "next/link";
import { Card } from "@/components/common";
import { formatShortDateTime } from "@/lib/format";
import type { RoomRequestSummary } from "@/types/room";

/**
 * RequestSummaryBar — 방 머리에 붙는 요청서 요약 줄.
 *   요청서 | 요구사항 3건 | 8/18 16:40 갱신됨 | 보기 ›
 *
 * 요청서는 방이 아니라 프로젝트에 달리므로 상담·컨설팅·개발 방이 모두 같은 줄을 봅니다.
 */
export interface RequestSummaryBarProps {
  request: RoomRequestSummary;
  /** 요청서 상세로 가는 경로. */
  href: string;
}

export function RequestSummaryBar({ request, href }: RequestSummaryBarProps) {
  return (
    <Card variant="surface" padding="none">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3.5 sm:px-5">
        <span className="text-sm font-semibold text-fg">요청서</span>
        <span className="text-sm text-fg-secondary">요구사항 {request.issueCount}건</span>

        <span className="ml-auto font-mono text-[13px] text-fg-subtle tabular-nums">
          {formatShortDateTime(request.updatedAt)} 갱신됨
        </span>
        <Link
          href={href}
          className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          보기 ›
        </Link>
      </div>
    </Card>
  );
}

/** 요청서가 아직 없는 방(진단 전)에 대신 놓는 줄. */
export function RequestSummaryEmpty() {
  return (
    <Card variant="surface" padding="none">
      <div className="px-4 py-3.5 text-sm text-fg-muted sm:px-5">
        아직 요청서가 없습니다. 문제 진단을 마치면 여기에 붙습니다.
      </div>
    </Card>
  );
}
