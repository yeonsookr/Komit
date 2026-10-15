import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { SessionStage } from "@/components/domains/room";
import { Callout } from "@/components/feedback";
import { Container } from "@/components/layouts";
import {
  getConsultingSession,
  getContract,
  getMe,
  getRoom,
  getSessionMessages,
  getSessionParticipations,
} from "@/lib/api/services";
import { formatSessionSchedule } from "@/lib/format";
import { isReceived } from "@/lib/rooms";
import { ROUTES } from "@/lib/routes";
import { SESSION_OPEN_BEFORE_MINUTES, sessionPhase } from "@/lib/session";
import { ApiError } from "@/types/api";

/**
 * 세션 화면에 필요한 것들.
 *
 * 세션 → 계약 → 방 순으로 한 번씩 물어야 합니다. 세션 응답에는 `contractId` 만 있고
 * 방이 없습니다 — 세션은 계약에 달리고 계약이 방에 달리기 때문입니다. 방까지 가는
 * 것은 화면 머리에 적을 프로젝트 이름과 상대 이름 때문이고, 나가는 길(「컨설팅 방」)도
 * 여기서 나옵니다.
 */
async function loadSession(sessionId: number) {
  try {
    const session = await getConsultingSession(sessionId);
    const contract = await getContract(session.contractId);
    const [room, me, participants] = await Promise.all([
      getRoom(contract.roomId),
      getMe(),
      getSessionParticipations(sessionId),
    ]);
    return { session, room, me, participants };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }
}

function parseSessionId(value: string): number {
  const id = Number(value);
  if (!Number.isInteger(id) || id <= 0) notFound();
  return id;
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}): Promise<Metadata> {
  const { sessionId } = await params;
  const id = Number(sessionId);
  if (!Number.isInteger(id)) return { title: "세션을 찾을 수 없습니다 | Komit" };

  const { room } = await loadSession(id);
  return {
    title: `${room.counterpartName}님과의 컨설팅 세션 | Komit`,
    description: `${room.projectName} 컨설팅 세션입니다.`,
  };
}

export default async function SessionPage({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = await params;
  const id = parseSessionId(sessionId);

  const { session, room, me, participants } = await loadSession(id);
  const phase = sessionPhase(session);

  /*
    끝난 세션은 이 화면에 머물 이유가 없습니다. 무대는 비어 있고 컨트롤은 누를 것이
    없으니, 볼 것이 있는 곳(요약)으로 곧장 보냅니다.
  */
  if (phase === "ENDED") redirect(ROUTES.roomSummary(room.roomId));

  /*
    동의하지 않은 사람은 이 화면에 머물 수 없습니다. 동의는 방의 입장 안내에서만 받으므로
    (`R-CON-17`) 주소를 직접 열어 그 자리를 건너뛴 것입니다. 방으로 돌려보내면 배너의
    「입장」이 바로 그 안내를 띄웁니다.

    이 화면이 읽는 세션 대화도 동의한 사람에게만 열려 있어, 돌려보내지 않으면 안내 대신
    오류를 보게 됩니다.
  */
  const consented = participants.some((participant) => participant.userId === me.userId);
  if (!consented) redirect(ROUTES.room(room.roomId));

  const counterpart = isReceived(room, me)
    ? room.counterpartName
    : `${room.counterpartName} 개발자`;
  const schedule = formatSessionSchedule(session.reservation.scheduledAt);

  return (
    <Container width="full" className="py-6">
      <Link
        href={ROUTES.room(room.roomId)}
        className="inline-flex rounded-sm text-[13px] font-semibold text-fg-subtle hover:text-fg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        ← 컨설팅 방
      </Link>

      <h1 className="mt-3 text-base font-bold text-fg">컨설팅 · {counterpart}</h1>
      <p className="mt-0.5 text-[13px] text-fg-subtle">
        {room.projectName} · {session.durationMinutes}분 세션 · {schedule}
      </p>

      {phase === "BEFORE" ? (
        <Callout className="mt-4" tone="info" title="아직 들어갈 수 없습니다">
          {schedule}에 시작합니다. 시작 {SESSION_OPEN_BEFORE_MINUTES}분 전부터 들어갈 수
          있습니다. 그때가 되면 컨설팅 방에서 다시 들어와 주세요.
        </Callout>
      ) : (
        <SessionStage
          sessionId={session.sessionId}
          /* 양쪽이 모여 시작했으면 들어간 것이고, 아니면 아직 상대를 기다립니다. */
          entered={session.startedAt !== null}
          roomId={room.roomId}
          counterpartName={room.counterpartName}
          myName={me.name}
          myUserId={me.userId}
          chatRefId={session.chatRefId}
          initialMessages={(await getSessionMessages(session.sessionId)).content}
          /* 두 사람이 서로 다른 값을 받아야 합니다. 의뢰인이 물러서는 쪽입니다. */
          polite={!isReceived(room, me)}
        />
      )}
    </Container>
  );
}
