import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { buttonClass } from "@/components/buttons/buttonStyles";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { ConsultingPaymentForm, DepositForm } from "@/components/domains/contract";
import { RoomHeader } from "@/components/domains/room";
import {
  getContract,
  getDeposit,
  getMe,
  getPaymentMethods,
  getRoom,
} from "@/lib/api/services";
import { consultingPaymentStage, type ConsultingPaymentStage } from "@/lib/contract";
import { isReceived } from "@/lib/rooms";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract } from "@/types/contract";

async function load<T>(read: () => Promise<T>): Promise<T> {
  try {
    return await read();
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }
}

/** 예치된 금액. 예치를 건 적이 없으면 백엔드가 404 를 주고, 그때는 null 입니다. */
async function readDepositedAmount(contractId: number): Promise<number | null> {
  try {
    return (await getDeposit(contractId)).amount;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

/**
 * 방·내 정보·계약서·결제 수단을 함께 읽습니다.
 *
 * 결제 수단은 의뢰인이 실제로 돈을 낼 화면에서만 읽습니다. 읽을 때 아직 없으면 계좌를
 * 발급하므로, 돈을 내지 않는 사람에게 계좌가 생기면 안 됩니다. (R-PAY-09)
 */
async function loadDeposit(roomId: number) {
  return load(async () => {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    const received = isReceived(room, me);
    const contract: Contract | null =
      room.contractId === null ? null : await getContract(room.contractId);
    const development = contract?.development ?? null;
    const canDeposit = !received && development !== null;

    let stage: ConsultingPaymentStage | null = null;
    if (contract?.consulting) {
      stage = consultingPaymentStage(contract, received, null);
      if (stage === "pay") {
        stage = consultingPaymentStage(
          contract,
          received,
          await readDepositedAmount(contract.contractId),
        );
      }
    }

    const paymentMethods = canDeposit || stage === "pay" ? await getPaymentMethods() : [];
    return { room, me, contract, development, canDeposit, stage, paymentMethods };
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
  const consulting = room.type === "CONSULTING";

  return {
    title: `${consulting ? "결제" : "예치"} · ${room.projectName} | Komit`,
    description: `${room.projectName} ${consulting ? "컨설팅" : "개발 계약"}의 대금을 맡깁니다.`,
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

  const { room, me, contract, development, canDeposit, stage, paymentMethods } =
    await loadDeposit(id);

  const contractLink = (
    <Link href={ROUTES.roomContract(room.roomId)} className={buttonClass({ variant: "outline", size: "sm" })}>
      계약서 보기
    </Link>
  );
  const developerSigned = contract?.developerSignature?.isSigned === true;

  if (contract?.consulting && stage !== null) {
    return (
      <Container className="py-8 sm:py-10">
        <RoomHeader room={room} me={me} contract={contract} />
        {stage === "pay" ? (
          <ConsultingPaymentForm
            contractId={contract.contractId}
            roomId={room.roomId}
            consulting={contract.consulting}
            paymentMethods={paymentMethods}
            counterpartName={room.counterpartName}
            sentAt={contract.updatedAt}
            developerSigned={developerSigned}
          />
        ) : stage === "paid" ? (
          <EmptyState
            className="mt-8"
            title="결제를 마쳤습니다"
            description={
              developerSigned
                ? "세션 시간이 되면 방에서 입장할 수 있습니다."
                : "개발자가 서명하면 세션이 확정됩니다."
            }
            action={contractLink}
          />
        ) : stage === "unsigned" ? (
          <EmptyState
            className="mt-8"
            title="계약서에 서명한 뒤에 결제할 수 있습니다"
            description="계약서를 확인하고 서명해 주세요."
            action={contractLink}
          />
        ) : (
          <EmptyState
            className="mt-8"
            title={stage === "cancelled" ? "취소된 계약입니다" : "결제는 의뢰인이 합니다"}
          />
        )}
      </Container>
    );
  }

  return (
    <Container className="py-8 sm:py-10">
      <RoomHeader room={room} me={me} contract={contract} />

      {canDeposit && contract && development ? (
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
