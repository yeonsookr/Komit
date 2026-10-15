import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/layouts";
import { RoomConversation, RoomHeader, SessionBanner } from "@/components/domains/room";
import { getContract, getMe, getRoom, getRoomMessages } from "@/lib/api/services";
import { isReceived, ROOM_TYPE_LABEL } from "@/lib/rooms";
import { confirmedSession, sessionPhase } from "@/lib/session";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";

/** 방과 내 정보를 함께 읽습니다. 없는 방이면 404 화면, 로그인이 풀렸으면 로그인으로 보냅니다. */
async function loadRoom(roomId: number) {
  try {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    return { room, me };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
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

  const { room, me } = await loadRoom(id);
  const title = isReceived(room, me)
    ? `${room.counterpartName}와 ${ROOM_TYPE_LABEL[room.type]}`
    : room.projectName;

  return {
    title: `${title} | Komit`,
    description: `${room.projectName} · ${room.counterpartName}님과의 ${ROOM_TYPE_LABEL[room.type]} 대화입니다.`,
  };
}

export default async function RoomPage({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me } = await loadRoom(id);
  const messages = await getRoomMessages(id);
  // 계약서는 갈래가 정해진 방에만 있습니다. 머리의 계약서 줄을 채우는 값입니다.
  const contract: Contract | null =
    room.contractId === null ? null : await getContract(room.contractId);

  /*
    컨설팅 방에서는 대화 위에 세션 배너가 붙습니다. 세션은 탭이 아니라 여기서
    들어갑니다 — 잡힌 시각에만 들어가는 것이라 탭으로 두면 그 밖의 때에 갈 곳이 없습니다.
    어느 때인지는 서버가 그릴 때 정합니다(`lib/session.ts`).
  */
  const session = confirmedSession(contract);

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {/* 갈래는 의뢰인이 정합니다 — 개발자에게는 버튼을 보이지 않습니다. */}
      <RoomConversation
        room={room}
        initialMessages={messages.content}
        myName={me.name}
        myUserId={me.userId}
        canConvert={!isReceived(room, me)}
        banner={
          session === null ? undefined : (
            <SessionBanner
              session={session}
              phase={sessionPhase(session)}
              roomId={room.roomId}
            />
          )
        }
      />
    </Container>
  );
}
