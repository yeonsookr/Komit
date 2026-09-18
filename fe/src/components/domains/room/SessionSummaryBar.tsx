import { Card } from "@/components/common";
import { formatDateLabel, formatShortDateTime } from "@/lib/format";
import { sessionElapsedMinutes } from "@/lib/session";
import type { ConsultingSession } from "@/types/contract";

export interface SessionSummaryBarProps {
  /** 이 방의 확정된 세션. 아직 잡히지 않았으면 null 입니다. */
  session: ConsultingSession | null;
  /**
   * 요약이 갱신된 시각. 요약을 읽는 화면만 알고 있는 값입니다.
   *
   * 다른 탭에서까지 요약을 한 번 더 부르지 않으려고 선택값으로 둡니다. 없으면 세션이
   * 끝난 시각을 적습니다 — 요약은 세션이 끝난 직후에 만들어지므로 몇 분 차이입니다.
   */
  summaryUpdatedAt?: string | null;
}

/**
 * SessionSummaryBar — 컨설팅 방 머리의 세션 요약 줄.
 *   세션 요약 | 32분 · 8월 29일 | 8/29 14:35 갱신됨
 *
 * 요청서·계약서 줄과 나란한 세 번째 산출물입니다. 컨설팅에서 손에 남는 것은 계약서와
 * 이 요약 둘뿐이라, 세션을 하기 전에도 자리를 비워 두고 「없음」이라고 적습니다.
 */
export function SessionSummaryBar({
  session,
  summaryUpdatedAt = null,
}: Readonly<SessionSummaryBarProps>) {
  const elapsed = session === null ? null : sessionElapsedMinutes(session);
  const updatedAt = summaryUpdatedAt ?? session?.endedAt ?? null;

  return (
    <Card variant="surface" padding="none">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3.5 sm:px-5">
        <span className="text-sm font-semibold text-fg">세션 요약</span>

        {/*
          세션을 마쳐야 요약이 생깁니다. 마친 뒤에도 잰 시간은 없을 수 있어(시작
          시각이 남지 않은 경우) 날짜만 적는 갈래를 둡니다 — 요약이 있는데 「없음」이라고
          적으면 아래에 요약이 펼쳐져 있는 화면과 어긋납니다.
        */}
        {session === null || updatedAt === null ? (
          <span className="text-sm text-fg-muted">없음</span>
        ) : (
          <span className="text-sm text-fg-secondary">
            {elapsed === null ? "" : `${elapsed}분 · `}
            {formatDateLabel(session.reservation.scheduledAt)}
          </span>
        )}

        <span className="ml-auto font-mono text-[13px] text-fg-subtle tabular-nums">
          {updatedAt === null ? "—" : `${formatShortDateTime(updatedAt)} 갱신됨`}
        </span>
      </div>
    </Card>
  );
}
