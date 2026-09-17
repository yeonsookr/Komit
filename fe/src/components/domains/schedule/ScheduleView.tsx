import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { Badge, Card } from "@/components/common";
import { EmptyState } from "@/components/feedback";
import { formatDateLabel, formatDateWithWeekday, formatTime } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type { MySchedule, MySession, ScheduleRole } from "@/types/schedule";

export interface ScheduleViewProps {
  schedule: MySchedule;
  now: string;
}

const ROLE_LABEL: Record<ScheduleRole, string> = {
  CLIENT: "의뢰한 상담",
  DEVELOPER: "맡은 상담",
};

function startLabel(scheduledAt: string, now: string): string | null {
  const minutes = Math.ceil(
    (new Date(scheduledAt).getTime() - new Date(now).getTime()) / 60_000,
  );
  if (minutes <= 0 || minutes > 60) return null;
  return `시작 ${minutes}분 전`;
}

function followUpLabel(until: string | null): string | null {
  return until ? `${formatDateLabel(until)}까지 질문할 수 있습니다` : null;
}

function SessionRole({ role }: Readonly<{ role: ScheduleRole }>) {
  return (
    <Badge tone={role === "CLIENT" ? "info" : "brand"} shape="square" size="sm">
      {ROLE_LABEL[role]}
    </Badge>
  );
}

function UpcomingSession({ session, now }: Readonly<{ session: MySession; now: string }>) {
  const startsIn = startLabel(session.scheduledAt, now);

  return (
    <Card as="article" variant="soft" className="flex flex-col gap-5 sm:flex-row sm:items-center">
      <div className="min-w-[116px]">
        <time className="text-xs text-fg-subtle" dateTime={session.scheduledAt}>
          {formatDateWithWeekday(session.scheduledAt)}
        </time>
        <p className="mt-1 font-mono text-xl font-bold text-fg tabular-nums">
          {formatTime(session.scheduledAt)}
        </p>
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <SessionRole role={session.role} />
          <h3 className="font-semibold text-fg">
            {session.counterpartName} · {session.durationMinutes}분
          </h3>
        </div>
        <p className="mt-2 text-[13px] text-fg-muted">
          {session.role === "CLIENT"
            ? "예약 시각에 개발자와 상담을 시작합니다"
            : "예약 시각에 의뢰인과 상담을 시작합니다"}
        </p>
      </div>

      <div className="flex shrink-0 flex-wrap items-center gap-4 sm:justify-end">
        {startsIn && (
          <span className="text-xs font-semibold text-primary">● {startsIn}</span>
        )}
        <Link href={ROUTES.room(session.roomId)} className={buttonClass({ size: "sm" })}>
          세션 방 들어가기
        </Link>
      </div>
    </Card>
  );
}

function PastSession({ session }: Readonly<{ session: MySession }>) {
  const payment = `${session.price.toLocaleString("ko-KR")}원 ${
    session.role === "CLIENT" ? "결제" : "받음"
  }`;
  const followUp = followUpLabel(session.followUpUntil);

  return (
    <li className="flex flex-col gap-3 py-5 sm:flex-row sm:items-center">
      <SessionRole role={session.role} />
      <div className="min-w-0 flex-1">
        <h3 className="text-sm font-semibold text-fg">
          {session.counterpartName} · {formatDateLabel(session.scheduledAt)} · {session.durationMinutes}분
        </h3>
        <p className="mt-1 text-[13px] text-fg-muted">
          세션 마침 · {payment}
          {followUp && ` · ${followUp}`}
        </p>
      </div>
      <Link
        href={ROUTES.room(session.roomId)}
        className={buttonClass({ variant: "ghost", size: "sm" })}
      >
        대화 열기
      </Link>
    </li>
  );
}

/** 의뢰인과 개발자로 참여한 일정을 한 화면에 시간 순서대로 나눠 보여줍니다. */
export function ScheduleView({ schedule, now }: Readonly<ScheduleViewProps>) {
  if (schedule.upcoming.length === 0 && schedule.past.length === 0) {
    return (
      <EmptyState
        className="mt-8"
        title="아직 일정이 없습니다"
        description="컨설팅 일정이 확정되면 잡힌 세션과 지난 세션을 여기에서 확인할 수 있습니다."
      />
    );
  }

  return (
    <>
      <section className="mt-10" aria-labelledby="upcoming-session-heading">
        <div className="flex items-center justify-between gap-4 border-b border-line pb-3">
          <h2 id="upcoming-session-heading" className="text-lg font-bold text-fg">
            잡힌 세션
          </h2>
          <span className="text-sm text-fg-subtle">{schedule.upcoming.length}건</span>
        </div>
        {schedule.upcoming.length > 0 ? (
          <div className="mt-3 space-y-3">
            {schedule.upcoming.map((session) => (
              <UpcomingSession key={session.sessionId} session={session} now={now} />
            ))}
          </div>
        ) : (
          <p className="py-8 text-sm text-fg-muted">잡힌 세션이 없습니다.</p>
        )}
      </section>

      <section className="mt-10" aria-labelledby="past-session-heading">
        <div className="flex items-center justify-between gap-4 border-b border-line pb-3">
          <h2 id="past-session-heading" className="text-lg font-bold text-fg">
            지난 세션
          </h2>
          <span className="text-sm text-fg-subtle">{schedule.past.length}건</span>
        </div>
        {schedule.past.length > 0 ? (
          <ul className="divide-y divide-line-subtle">
            {schedule.past.map((session) => (
              <PastSession key={session.sessionId} session={session} />
            ))}
          </ul>
        ) : (
          <p className="py-8 text-sm text-fg-muted">지난 세션이 없습니다.</p>
        )}
      </section>
    </>
  );
}
