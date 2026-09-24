import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { buttonClass } from "@/components/buttons/buttonStyles";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { RequirementList } from "@/components/domains/checkup";
import { RoomHeader } from "@/components/domains/room";
import { getContract, getIssues, getMe, getRoom } from "@/lib/api/services";
import { isInRequest } from "@/lib/diagnosisReport";
import { isReceived } from "@/lib/rooms";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";

/**
 * 방·내 정보·계약서와 요청서를 함께 읽습니다.
 *
 * 요청서는 방 응답이 가리키는 진단에서 읽습니다. 서버가 요청을 받은 개발자에게는 요청서에 담긴
 * 것만 내려주므로 (`R-REQ-05`), 화면은 받은 것을 그대로 그립니다.
 */
async function loadRequest(roomId: number) {
  try {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    const diagnosisId = room.request?.diagnosisId ?? null;
    const [contract, issues] = await Promise.all([
      room.contractId === null
        ? Promise.resolve(null)
        : (getContract(room.contractId) as Promise<Contract | null>),
      diagnosisId === null ? Promise.resolve([]) : getIssues(diagnosisId),
    ]);
    return { room, me, contract, issues };
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

  const { room } = await loadRequest(id);

  return {
    title: `요청서 · ${room.projectName} | Komit`,
    description: `${room.projectName}에서 고쳐 달라고 온 요구사항입니다.`,
  };
}

/**
 * 방에서 여는 요청서.
 *
 * <b>읽기만 합니다.</b> 요구사항을 더하거나 해결로 내리거나 다시 검사하는 일은 의뢰인이 사전점검
 * 화면에서 합니다 (`R-REQ-05`). 여기에 버튼을 두지 않는 것은 숨기는 것이 아니라 이 화면이 할 일이
 * 아니기 때문입니다.
 */
export default async function RoomRequestPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me, contract, issues } = await loadRequest(id);
  const requirements = issues.filter(isInRequest);

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {room.request === null ? (
        <EmptyState
          className="mt-8"
          title="아직 요청서가 없습니다"
          description="의뢰인이 사전점검을 마치고 요구사항을 보내면 여기에 나타납니다."
        />
      ) : (
        <section className="mt-8">
          <div className="flex items-baseline justify-between gap-4">
            <h2 className="text-base font-bold text-fg">요구사항</h2>
            <p className="text-[13px] text-fg-subtle">
              <span className="font-mono tabular-nums">{requirements.length}</span>건
            </p>
          </div>

          <RequirementList
            requirements={requirements}
            emptyTitle="요청서에 담긴 요구사항이 없습니다"
            emptyDescription="의뢰인이 요구사항을 다시 고르면 여기에 나타납니다."
          />

          {/*
            의뢰인에게만 고치러 갈 길을 얹습니다. 개발자에게 보이면 안 되는 것을 가리는 것이
            아니라, 의뢰인이 방에서 사전점검 화면으로 건너갈 자리가 없어 두는 것입니다.
          */}
          {!isReceived(room, me) && (
            <div className="mt-6 flex justify-end">
              <Link
                href={ROUTES.checkupRequest(room.request.diagnosisId)}
                className={buttonClass({ variant: "outline" })}
              >
                요청서 수정하기
              </Link>
            </div>
          )}
        </section>
      )}
    </Container>
  );
}
