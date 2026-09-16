import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { ReviewForm } from "@/components/domains/contract";
import { RoomHeader } from "@/components/domains/room";
import { getContract, getMe, getRoom } from "@/lib/api/services";
import { isCompleted } from "@/lib/contract";
import { isReceived } from "@/lib/rooms";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";

/** 방·내 정보·계약서를 함께 읽습니다. 없는 방이면 404 화면으로 보냅니다. */
async function loadReview(roomId: number) {
  try {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    const contract: Contract | null =
      room.contractId === null ? null : await getContract(room.contractId);
    return { room, me, contract };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
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

  const { room } = await loadReview(id);

  return {
    title: `리뷰 · ${room.projectName} | Komit`,
    description: `${room.counterpartName} 개발자에게 남기는 리뷰입니다.`,
  };
}

export default async function RoomReviewPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract } = await loadReview(id);

  /**
   * 리뷰는 유료 거래를 마친 의뢰인만 남깁니다 (R-REV-02). 컨설팅과 개발 둘 다
   * 유료라 갈래를 가리지 않습니다.
   * 개발자는 받은 리뷰에 답글을 쓸 수 있지만 그건 내 프로필 화면의 일입니다.
   */
  const canWrite = !isReceived(room, me) && contract !== null && isCompleted(contract);

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {canWrite && contract ? (
        <ReviewForm
          contractId={contract.contractId}
          roomId={room.roomId}
          // 마지막으로 통과 확정된 작업의 시각이 곧 개발 완료 시각입니다.
          completedAt={
            contract.development?.milestones
              .map((milestone) => milestone.confirmedAt)
              .filter((at): at is string => at !== null)
              .sort()
              .at(-1) ?? null
          }
        />
      ) : (
        <EmptyState
          title={isReceived(room, me) ? "리뷰는 의뢰인이 남깁니다" : "아직 리뷰를 남길 수 없습니다"}
          description={
            isReceived(room, me)
              ? "받은 리뷰에는 내 프로필 화면에서 답글을 쓸 수 있습니다."
              : "작업이 모두 끝나고 정산이 마무리되면 여기에서 리뷰를 남깁니다."
          }
        />
      )}
    </Container>
  );
}
