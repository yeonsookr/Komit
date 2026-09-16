import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/layouts";
import { ContractDetail } from "@/components/domains/contract";
import { RoomHeader } from "@/components/domains/room";
import { getContract, getMe, getRoom } from "@/lib/api/services";
import { isReceived, ROOM_TYPE_LABEL } from "@/lib/rooms";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";

/** 방·내 정보·계약서를 함께 읽습니다. 없는 방이면 404 화면,
 * 로그인이 풀렸으면 로그인으로 보냅니다. */
async function loadContract(roomId: number) {
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

export async function generateMetadata({
  params,
}: {
  params: Promise<{ roomId: string }>;
}): Promise<Metadata> {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id)) return { title: "방을 찾을 수 없습니다 | Komit" };

  const { room } = await loadContract(id);

  return {
    title: `계약서 · ${room.projectName} | Komit`,
    description: `${room.counterpartName}님과의 ${ROOM_TYPE_LABEL[room.type]} 계약서입니다.`,
  };
}

export default async function RoomContractPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract } = await loadContract(id);

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />
      <ContractDetail
        roomId={room.roomId}
        roomTitle={room.projectName}
        counterpartName={room.counterpartName}
        contract={contract}
        received={isReceived(room, me)}
        myUserId={me.userId}
      />
    </Container>
  );
}
