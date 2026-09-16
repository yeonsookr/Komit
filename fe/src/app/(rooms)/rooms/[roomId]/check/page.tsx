import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { QaCheckPanel, QaSubmitPanel } from "@/components/domains/contract";
import { RoomHeader } from "@/components/domains/room";
import { getContract, getMe, getRoom } from "@/lib/api/services";
import { isReceived } from "@/lib/rooms";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";

/** 방·내 정보·계약서를 함께 읽습니다. 없는 방이면 404 화면,
 * 로그인이 풀렸으면 로그인으로 보냅니다. */
async function loadCheck(roomId: number) {
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

  const { room } = await loadCheck(id);

  return {
    title: `확인 · ${room.projectName} | Komit`,
    description: `${room.projectName} 개발 계약의 작업 확인입니다.`,
  };
}

export default async function RoomCheckPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract } = await loadCheck(id);
  const development = contract?.development ?? null;

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {development === null ? (
        <EmptyState
          title="아직 확인할 작업이 없습니다"
          description="계약서에 양쪽이 서명하고 개발자가 착수하면 여기에서 작업을 확인합니다."
        />
      ) : isReceived(room, me) ? (
        // 통과 판정은 의뢰인이 합니다. 개발자는 확인 요청을 보내고 상태를 봅니다.
        <QaSubmitPanel
          roomId={room.roomId}
          development={development}
          counterpartName={room.counterpartName}
        />
      ) : (
        <QaCheckPanel roomId={room.roomId} development={development} />
      )}
    </Container>
  );
}
