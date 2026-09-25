import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { RoomHeader, SessionSummaryView } from "@/components/domains/room";
import { Callout } from "@/components/feedback";
import { Container } from "@/components/layouts";
import { getContract, getMe, getRoom, getSessionSummary } from "@/lib/api/services";
import { formatSessionSchedule } from "@/lib/format";
import { isReceived } from "@/lib/rooms";
import { ROUTES } from "@/lib/routes";
import { confirmedSession, sessionPhase } from "@/lib/session";
import { ApiError } from "@/types/api";
import type { Contract, SessionSummary } from "@/types/contract";

/**
 * 요약은 세션에 달려 있고 세션은 계약서 안에 있습니다. 방 → 계약서 → 세션 →
 * 요약 순으로 한 번씩 물어야 이 화면이 그려집니다.
 */
async function loadSummary(roomId: number) {
  try {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    const contract: Contract | null =
      room.contractId === null ? null : await getContract(room.contractId);
    return { room, me, contract };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }
}

/**
 * 요약 본문.
 *
 * 세션이 끝나도 요약이 바로 서지는 않습니다 — 녹음을 글로 옮기고 개발자가 검토하는
 * 동안 서버가 409 를 줍니다. 그 사이를 오류로 다루면 다 된 화면에 빨간 칸이 뜨므로,
 * 「만드는 중」과 「읽지 못함」을 갈라 앞쪽만 여기서 삼킵니다.
 */
async function loadSessionSummary(sessionId: number): Promise<SessionSummary | null> {
  try {
    return await getSessionSummary(sessionId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) return null;
    throw error;
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ roomId: string }>;
}): Promise<Metadata> {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id)) return { title: "방을 찾을 수 없습니다 | Komit" };

  const { room } = await loadSummary(id);
  return {
    title: `세션 요약 · ${room.projectName} | Komit`,
    description: `${room.counterpartName}님과의 컨설팅 세션 요약입니다.`,
  };
}

export default async function RoomSummaryPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract } = await loadSummary(id);

  // 요약 탭은 컨설팅 방에만 있습니다(`RoomTabs`). 주소로 바로 들어와도 같습니다.
  if (room.type !== "CONSULTING") notFound();

  /*
    요약이 섰는지는 서버가 압니다 — 아직이면 409 입니다. 여기서 「세션이 끝났는가」로
    한 번 더 가르지 않습니다. 끝난 시각과 요약이 서는 시점은 같지 않아, 화면이 제
    나름대로 판정하면 서버가 200 을 주는데도 안내만 띄우는 일이 생깁니다.
    화면이 쓰는 것은 서버가 아직 아니라고 했을 때 무슨 말을 적을지뿐입니다.
  */
  const session = confirmedSession(contract);
  const summary = session === null ? null : await loadSessionSummary(session.sessionId);

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader
        room={room}
        me={me}
        contract={contract}
        summaryUpdatedAt={summary?.updatedAt ?? null}
      />

      {session !== null && summary !== null ? (
        <SessionSummaryView
          sessionId={session.sessionId}
          summary={summary}
          // 요청서는 의뢰인의 것입니다. 개발자는 요약을 읽기만 합니다.
          canApply={!isReceived(room, me)}
        />
      ) : (
        <Callout className="mt-8" tone="neutral" title={emptyTitle(session)}>
          {emptyBody(session)}
        </Callout>
      )}
    </Container>
  );
}

type Session = ReturnType<typeof confirmedSession>;

function emptyTitle(session: Session): string {
  if (session === null) return "아직 잡힌 세션이 없습니다";
  return sessionPhase(session) === "ENDED"
    ? "요약을 만들고 있습니다"
    : "세션이 끝나면 요약이 만들어집니다";
}

function emptyBody(session: Session): string {
  if (session === null) {
    return "계약서에 세션 일시가 확정되면 그 세션을 마친 뒤 요약이 생깁니다.";
  }
  if (sessionPhase(session) === "ENDED") {
    return "녹음을 글로 옮겨 정리하는 중입니다. 잠시 후 새로고침해 주세요.";
  }
  return `${formatSessionSchedule(session.reservation.scheduledAt)}에 시작합니다. 세션을 마치면 논의한 것과 결정된 것이 여기에 정리됩니다.`;
}
