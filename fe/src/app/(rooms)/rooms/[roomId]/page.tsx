import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/layouts";
import { RoomConversation, RoomHeader } from "@/components/domains/room";
import { getChatMessages, getContract, getMe, getRoom } from "@/lib/api/services";
import { isReceived, ROOM_TYPE_LABEL } from "@/lib/rooms";
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
  const messages = await getChatMessages(room.chatRefId);
  // 계약서는 갈래가 정해진 방에만 있습니다. 머리의 계약서 줄을 채우는 값입니다.
  const contract: Contract | null =
    room.contractId === null ? null : await getContract(room.contractId);

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {/* 갈래는 의뢰인이 정합니다 — 개발자에게는 버튼을 보이지 않습니다. */}
      <RoomConversation
        room={room}
        initialMessages={messages}
        myName={me.name}
        canConvert={!isReceived(room, me)}
      />
    </Container>
  );
}
