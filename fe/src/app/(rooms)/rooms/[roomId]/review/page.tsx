import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { ReviewForm, WrittenReview } from "@/components/domains/contract";
import { RoomHeader } from "@/components/domains/room";
import { getContract, getContractReview, getMe, getRoom } from "@/lib/api/services";
import { isCompleted } from "@/lib/contract";
import { formatDateLabel, formatDateTimeLabel } from "@/lib/format";
import { isReceived } from "@/lib/rooms";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";
import type { Review } from "@/types/developer";

/** 이 계약에 남긴 리뷰. 아직 없으면 백엔드가 404 를 주고, 그때는 null 입니다. */
async function readReview(contractId: number): Promise<Review | null> {
  try {
    return await getContractReview(contractId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

/** 없는 방이면 404 화면, 로그인이 풀렸으면 로그인으로 보냅니다. */
async function load<T>(read: () => Promise<T>): Promise<T> {
  try {
    return await read();
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }
}

/** 방·내 정보·계약서와 이 계약에 남긴 리뷰를 함께 읽습니다. */
async function loadReview(roomId: number) {
  return load(async () => {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    const contract: Contract | null =
      room.contractId === null ? null : await getContract(room.contractId);
    // 끝난 거래에만 리뷰가 달리므로 그 전에는 묻지 않습니다.
    const review =
      contract !== null && isCompleted(contract) ? await readReview(contract.contractId) : null;
    return { room, me, contract, review };
  });
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ roomId: string }>;
}): Promise<Metadata> {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id)) return { title: "방을 찾을 수 없습니다 | Komit" };

  const room = await load(() => getRoom(id));

  return {
    title: `리뷰 · ${room.projectName} | Komit`,
    description: `${room.counterpartName} 개발자에게 남기는 리뷰입니다.`,
  };
}

/** 리뷰 머리말. 컨설팅은 세션이 끝난 시각, 개발은 마지막 작업이 통과된 날을 적습니다. */
function completionNote(contract: Contract): string {
  if (contract.consulting) {
    const endedAt = contract.consulting.sessions
      .map((session) => session.endedAt)
      .filter((at): at is string => at !== null)
      .sort()
      .at(-1);
    return endedAt ? `세션 완료 ${formatDateTimeLabel(endedAt)}` : "세션 완료";
  }
  const confirmedAt = contract.development?.milestones
    .map((milestone) => milestone.confirmedAt)
    .filter((at): at is string => at !== null)
    .sort()
    .at(-1);
  return confirmedAt ? `개발 완료 ${formatDateLabel(confirmedAt)} · 정산 완료` : "정산 완료";
}

export default async function RoomReviewPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract, review } = await loadReview(id);

  /**
   * 리뷰는 유료 거래를 마친 의뢰인만 남깁니다 (R-REV-02). 컨설팅과 개발 둘 다
   * 유료라 갈래를 가리지 않습니다.
   * 개발자는 받은 리뷰에 답글을 쓸 수 있지만 그건 내 프로필 화면의 일입니다.
   */
  const canWrite = !isReceived(room, me) && contract !== null && isCompleted(contract);

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {review ? (
        <WrittenReview review={review} received={isReceived(room, me)} />
      ) : canWrite && contract ? (
        <ReviewForm
          contractId={contract.contractId}
          roomId={room.roomId}
          note={completionNote(contract)}
        />
      ) : (
        <EmptyState
          title={isReceived(room, me) ? "리뷰는 의뢰인이 남깁니다" : "아직 리뷰를 남길 수 없습니다"}
          description={
            isReceived(room, me)
              ? "받은 리뷰에는 내 프로필 화면에서 답글을 쓸 수 있습니다."
              : contract?.consulting
                ? "세션이 끝나면 여기에서 리뷰를 남깁니다."
                : "작업이 모두 끝나고 정산이 마무리되면 여기에서 리뷰를 남깁니다."
          }
        />
      )}
    </Container>
  );
}
