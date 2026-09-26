import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { ConsultingContractForm, ContractEditor } from "@/components/domains/contract";
import { RoomHeader } from "@/components/domains/room";
import { getContract, getMe, getRoom } from "@/lib/api/services";
import { hasAnySignature } from "@/lib/contract";
import { isReceived } from "@/lib/rooms";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";

/** 갈래마다 계약의 내용이 달라 제목도 갈립니다. */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ roomId: string }>;
}): Promise<Metadata> {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id)) return { title: "방을 찾을 수 없습니다 | Komit" };

  const { room } = await loadEditor(id);
  return {
    title: room.type === "CONSULTING" ? "컨설팅 계약서 수정 | Komit" : "계약서 수정 | Komit",
    description: "보낸 계약서를 고쳐 다시 보냅니다.",
  };
}

/**
 * 방·내 정보·계약서를 읽습니다. 작업이 붙은 요구사항은 계약서의 작업마다 들어 있습니다.
 */
async function loadEditor(roomId: number) {
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

export default async function ContractEditPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract } = await loadEditor(id);

  /** 계약서는 개발자가 씁니다. 의뢰인은 받아서 서명만 합니다. */
  const canWrite = isReceived(room, me);

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {canWrite && contract !== null && !hasAnySignature(contract) &&
      room.type === "CONSULTING" ? (
        <ConsultingContractForm
          roomId={room.roomId}
          counterpartName={room.counterpartName}
          contract={contract}
        />
      ) : canWrite && contract !== null && !hasAnySignature(contract) ? (
        <ContractEditor
          roomId={room.roomId}
          contract={contract}
          counterpartName={room.counterpartName}
        />
      ) : (
        <EmptyState
          title={
            contract !== null && hasAnySignature(contract)
              ? "서명한 계약서는 고칠 수 없습니다"
              : "고칠 계약서가 없습니다"
          }
          description={
            contract !== null && hasAnySignature(contract)
              ? "바꿔야 할 것이 있으면 계약서를 새로 써서 다시 서명합니다."
              : "계약서를 먼저 작성해 주세요."
          }
        />
      )}
    </Container>
  );
}
