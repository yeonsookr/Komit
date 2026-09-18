import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { ConsultingContractForm, ContractEditor } from "@/components/domains/contract";
import { RoomHeader } from "@/components/domains/room";
import { getContract, getIssues, getMe, getRoom } from "@/lib/api/services";
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
  return room.type === "CONSULTING"
    ? {
        title: "컨설팅 계약서 작성 | Komit",
        description: "세션 구성과 시간, 가격을 정해 계약서를 보냅니다.",
      }
    : {
        title: "개발 계약서 작성 | Komit",
        description: "맡을 범위와 금액, 기한을 정해 계약서를 보냅니다.",
      };
}

/**
 * 방·내 정보·계약서와 요청서의 요구사항을 읽습니다.
 * 계약서는 요청서의 요구사항을 작업으로 나눈 것이라 그 식별자가 필요합니다.
 */
async function loadEditor(roomId: number) {
  try {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    const contract: Contract | null =
      room.contractId === null ? null : await getContract(room.contractId);
    const issues =
      room.request === null ? [] : await getIssues(room.request.diagnosisId);
    return { room, me, contract, issues };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }
}

export default async function ContractNewPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract, issues } = await loadEditor(id);

  /** 계약서는 개발자가 씁니다. 의뢰인은 받아서 서명만 합니다. */
  const canWrite = isReceived(room, me);

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {/* 갈래마다 계약의 내용이 달라 폼이 다릅니다. 컨설팅은 세션 하나가 계약의 전부입니다. */}
      {canWrite && room.type === "CONSULTING" ? (
        <ConsultingContractForm
          roomId={room.roomId}
          counterpartName={room.counterpartName}
        />
      ) : canWrite ? (
        <ContractEditor
          roomId={room.roomId}
          contract={null}
          issueIds={issues.map((issue) => issue.issueId)}
          counterpartName={room.counterpartName}
        />
      ) : (
        <EmptyState
          title="계약서는 개발자가 씁니다"
          description="개발자가 범위와 금액, 기한을 정리해 보내면 계약서 탭에서 볼 수 있습니다."
        />
      )}
    </Container>
  );
}
