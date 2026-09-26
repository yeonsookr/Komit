import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { ProgressPanel, StartWorkPanel } from "@/components/domains/contract";
import { RoomHeader } from "@/components/domains/room";
import { getContract, getDeposit, getMe, getRoom } from "@/lib/api/services";
import { isReceived } from "@/lib/rooms";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract, EscrowDeposit } from "@/types/contract";

/**
 * 방·내 정보·계약서·예치 상태를 읽습니다.
 *
 * 예치는 아직 걸지 않았으면 404 라 없는 것으로 봅니다 — 방이나 계약서가 없는
 * 것과 달리 화면을 못 그릴 이유가 아닙니다.
 */
async function loadProgress(roomId: number) {
  try {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    const contract: Contract | null =
      room.contractId === null ? null : await getContract(room.contractId);

    let deposit: EscrowDeposit | null = null;
    if (contract !== null) {
      deposit = await getDeposit(contract.contractId).catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 404) return null;
        throw error;
      });
    }

    return { room, me, contract, deposit };
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

  const { room } = await loadProgress(id);

  return {
    title: `진행 · ${room.projectName} | Komit`,
    description: `${room.projectName} 개발 계약의 진행 상황입니다.`,
  };
}

export default async function RoomProgressPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract, deposit } = await loadProgress(id);
  const received = isReceived(room, me);
  /**
   * 개발 쪽 상태는 계약서를 처음 쓸 때부터 `SIGNED` 라 양쪽이 서명했는지 알려 주지 않습니다.
   * 계약이 성립했는지는 공통 계약 상태로 봅니다 (R-CON-01). 성립 전에는 진행할 것이 없습니다.
   */
  const development = contract?.status === "SIGNED" ? contract.development : null;

  /**
   * 개발자는 착수 전까지 착수 화면을 봅니다. 착수하면 의뢰인과 같은 진행 상황을
   * 봅니다 — 진행 중에 개발자가 따로 볼 것은 확인 탭에 있습니다.
   */
  const beforeStart =
    received && (development?.status === "SIGNED" || development?.status === "DEPOSITED");

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {development === null ? (
        <EmptyState
          title="아직 진행할 것이 없습니다"
          description="계약서에 양쪽이 서명하고 예치가 끝나면 여기에서 진행 상황을 봅니다."
        />
      ) : beforeStart ? (
        <StartWorkPanel
          contractId={contract!.contractId}
          development={development}
          deposit={deposit}
          counterpartName={room.counterpartName}
        />
      ) : (
        <ProgressPanel
          roomId={room.roomId}
          development={development}
          deposit={deposit}
          counterpartName={room.counterpartName}
          received={received}
        />
      )}
    </Container>
  );
}
