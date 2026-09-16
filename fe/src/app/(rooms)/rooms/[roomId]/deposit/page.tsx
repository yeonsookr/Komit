import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { DepositForm } from "@/components/domains/contract";
import { RoomHeader } from "@/components/domains/room";
import { getContract, getMe, getPaymentMethods, getRoom } from "@/lib/api/services";
import { isReceived } from "@/lib/rooms";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";

/** 방·내 정보·계약서·결제 수단을 함께 읽습니다. */
async function loadDeposit(roomId: number) {
  try {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    const contract: Contract | null =
      room.contractId === null ? null : await getContract(room.contractId);
    const paymentMethods = contract === null ? [] : await getPaymentMethods();
    return { room, me, contract, paymentMethods };
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

  const { room } = await loadDeposit(id);

  return {
    title: `예치 · ${room.projectName} | Komit`,
    description: `${room.projectName} 개발 계약의 대금을 맡깁니다.`,
  };
}

export default async function RoomDepositPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract, paymentMethods } = await loadDeposit(id);
  const development = contract?.development ?? null;

  /** 예치는 의뢰인이 합니다. 개발자가 부르면 백엔드도 403 으로 막습니다. */
  const canDeposit = !isReceived(room, me) && development !== null;

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {canDeposit && contract ? (
        <DepositForm
          contractId={contract.contractId}
          roomId={room.roomId}
          development={development}
          paymentMethods={paymentMethods}
          requestHref={
            room.request === null ? null : ROUTES.checkupRequest(room.request.diagnosisId)
          }
          counterpartName={room.counterpartName}
          sentAt={contract.updatedAt}
        />
      ) : (
        <EmptyState
          title={isReceived(room, me) ? "예치는 의뢰인이 합니다" : "아직 예치할 것이 없습니다"}
          description={
            isReceived(room, me)
              ? "의뢰인이 서명하고 예치를 마치면 진행 탭에서 착수할 수 있습니다."
              : "개발자가 계약서를 보내면 여기에서 서명하고 예치합니다."
          }
        />
      )}
    </Container>
  );
}
