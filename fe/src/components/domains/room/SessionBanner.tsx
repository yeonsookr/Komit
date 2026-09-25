"use client";

import Link from "next/link";
import * as React from "react";
import { Button } from "@/components/buttons";
import { buttonClass } from "@/components/buttons/buttonStyles";
import { ChatSessionBanner } from "@/components/domains/chat";
import { SessionEntryDialog } from "./SessionEntryDialog";
import { formatSessionSchedule } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import {
  SESSION_OPEN_BEFORE_MINUTES,
  sessionElapsedMinutes,
  type SessionPhase,
} from "@/lib/session";
import type { ConsultingSession } from "@/types/contract";

export interface SessionBannerProps {
  session: ConsultingSession;
  /** 지금이 어느 때인지. 화면을 그리는 서버가 정해 내려보냅니다. */
  phase: SessionPhase;
  /** 요약을 보러 갈 방. 끝난 뒤 버튼이 씁니다. */
  roomId: number;
}

/**
 * SessionBanner — 컨설팅 방 채팅 위에 붙는 세션 안내 (`4_의뢰인_입장전`).
 *
 * **세션은 탭이 아니라 이 배너에서 들어갑니다.** 잡힌 시각에만 들어가는 것이라 탭으로
 * 두면 그 밖의 때에 갈 곳이 없습니다. 대화를 하다 때가 되면 같은 자리에서 들어갑니다.
 *
 * 세 때를 한 배너가 받습니다 (`4_의뢰인_입장전_상태`).
 *
 * | 때 | 적는 것 | 버튼 |
 * | --- | --- | --- |
 * | 아직 이름 | 잡힌 일시와 시간 | 없음 (언제부터 들어갈 수 있는지만) |
 * | 열림 | 같음 | 입장 |
 * | 끝남 | 실제로 한 시간 | 요약 보기 |
 *
 * 때는 서버가 그릴 때 정합니다. 화면을 열어 둔 채 시각이 지나면 바뀌지 않으므로,
 * 들어갈 수 있게 되었는데 버튼이 없으면 새로고침하면 됩니다. 1초마다 세어 다시 그리는
 * 것은 이 값이 분 단위로 바뀌는 데 비해 값이 맞지 않습니다.
 *
 * **입장은 바로 가지 않고 동의를 먼저 받습니다** (`R-CON-17`). 그 팝업이
 * `SessionEntryDialog` 입니다.
 */
export function SessionBanner({ session, phase, roomId }: Readonly<SessionBannerProps>) {
  const schedule = formatSessionSchedule(session.reservation.scheduledAt);
  const [asking, setAsking] = React.useState(false);

  if (phase === "ENDED") {
    const elapsed = sessionElapsedMinutes(session);
    return (
      <ChatSessionBanner
        className="bg-surface"
        title={`컨설팅 세션 · ${schedule}${elapsed === null ? "" : ` · ${elapsed}분 진행`}`}
        description="녹음을 바탕으로 요약을 만들었습니다"
        action={
          <Link href={ROUTES.roomSummary(roomId)} className={buttonClass({ variant: "outline" })}>
            요약 보기
          </Link>
        }
      />
    );
  }

  return (
    <ChatSessionBanner
      className="bg-primary-soft"
      title={`컨설팅 세션 · ${schedule} · ${session.durationMinutes}분`}
      description={
        phase === "OPEN"
          ? "지금 들어갈 수 있습니다"
          : `시작 ${SESSION_OPEN_BEFORE_MINUTES}분 전부터 들어갈 수 있습니다`
      }
      action={
        phase === "OPEN" ? (
          <>
            <Button onClick={() => setAsking(true)}>입장</Button>
            <SessionEntryDialog
              open={asking}
              onClose={() => setAsking(false)}
              sessionId={session.sessionId}
            />
          </>
        ) : undefined
      }
    />
  );
}
