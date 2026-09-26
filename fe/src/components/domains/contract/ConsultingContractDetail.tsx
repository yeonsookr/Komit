import Link from "next/link";
import { buttonClass } from "@/components/buttons/buttonStyles";
import { Card, CardHeader, KeyValueRow, Money } from "@/components/common";
import { Callout } from "@/components/feedback";
import {
  canCancel,
  hasAnySignature,
  hasSigned,
  isCancelled,
  isSignedByBoth,
  lastCounterpartMessage,
} from "@/lib/contract";
import { formatDateTimeLabel, formatSessionSchedule } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type { Contract } from "@/types/contract";
import { CancelContractButton } from "./CancelContractButton";
import { SignContractButton } from "./SignContractButton";

/** 계약서 제목 아래 한 줄. 취소가 서명보다 앞섭니다 — 취소된 계약은 성립하지 않습니다. */
function statusLine(cancelled: boolean, signed: boolean): string {
  if (cancelled) return "의뢰인이 계약을 취소했습니다.";
  if (signed) return "양쪽이 서명해 계약이 성립했습니다.";
  return "협의 중 · 아직 서명하지 않았습니다";
}

export interface ConsultingContractDetailProps {
  roomId: number;
  /** 방 이름. 서명판 부제에 들어갑니다. */
  roomTitle: string;
  counterpartName: string;
  contract: Contract;
  /** 내가 이 방에 개발자로 들어갔는지. 아래 버튼이 갈립니다. */
  received: boolean;
  myUserId: number;
}

/**
 * ConsultingContractDetail — 컨설팅 계약서 (`4_의뢰인_계약서상세`·`4_개발자_계약서검토`).
 *
 * **개발 계약서와 읽는 방식이 다릅니다.** 개발은 작업을 여러 개로 쪼개고 그 합이
 * 계약 금액이지만, 컨설팅은 한 번 만나는 것이 계약의 전부라 무엇을 다룰지·언제
 * 얼마나·얼마에가 조건의 전부입니다. 그래서 작업 목록 대신 세션 한 줄을 보여줍니다.
 *
 * 양쪽이 서명해야 성립하고 그 뒤에는 고칠 수 없습니다 (R-CON-01·R-CON-02).
 * 의뢰인은 서명하면서 세션 대금을 냅니다 (R-CON-07).
 *
 * 개발자가 서명하기 전이면 의뢰인이 계약을 취소할 수 있고, 취소된 계약은 서명·세션을 받지
 * 않습니다 (R-CON-21). 취소된 계약서는 내용만 보여주고 버튼을 모두 내립니다.
 */
export function ConsultingContractDetail({
  roomId,
  roomTitle,
  counterpartName,
  contract,
  received,
  myUserId,
}: Readonly<ConsultingContractDetailProps>) {
  const consulting = contract.consulting;
  if (consulting === null) return null;

  const signed = isSignedByBoth(contract);
  const signedByAnyone = hasAnySignature(contract);
  const cancelled = isCancelled(contract);
  const mine = hasSigned(contract, myUserId);
  // 지금은 계약 하나에 세션 하나입니다. 목 명세가 배열로 두어 나중에 늘릴 여지만 남겼습니다.
  const session = consulting.sessions[0] ?? null;
  const amount = consulting.totalAmount.toLocaleString("ko-KR");

  const note = lastCounterpartMessage(contract, received);

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">계약서</h2>
        <p className="mt-1 text-[13px] text-fg-subtle">
          {statusLine(cancelled, signed)}
        </p>
      </header>

      {cancelled && (
        <Callout tone="neutral" title="취소된 계약입니다." className="mt-6">
          {received
            ? "의뢰인이 계약을 취소해 서명할 수 없고, 이 계약의 세션도 열리지 않습니다."
            : "이 계약의 세션은 열리지 않습니다. 예치한 금액은 가입할 때 발급한 계좌로 돌아갑니다."}
        </Callout>
      )}

      <div className="mt-6">
        <CardHeader
          title="컨설팅 구성"
          action={<span className="text-[13px] text-fg-subtle">세션 {consulting.sessions.length}회</span>}
        />
        <Card className="mt-3" padding="none">
          <div className="divide-y divide-line-subtle">
            <KeyValueRow
              label="다룰 항목"
              value={consulting.topic}
              className="px-4 py-3.5 sm:px-5"
            />
            {consulting.detail !== null && (
              <KeyValueRow
                label="상담 내용"
                value={consulting.detail}
                className="px-4 py-3.5 sm:px-5"
              />
            )}
            <KeyValueRow
              label="날짜와 시간"
              value={
                session === null
                  ? "정하지 않음"
                  : formatSessionSchedule(session.reservation.scheduledAt)
              }
              mono={session !== null}
              className="px-4 py-3.5 sm:px-5"
            />
            <KeyValueRow
              label="시간"
              value={session === null ? "정하지 않음" : `${session.durationMinutes}분`}
              mono={session !== null}
              className="px-4 py-3.5 sm:px-5"
            />
            <KeyValueRow
              label="가격"
              value={<Money value={consulting.totalAmount} />}
              className="px-4 py-3.5 sm:px-5"
            />
          </div>
        </Card>
      </div>

      <div className="mt-10">
        <CardHeader title="계약 조건" />
        <Card className="mt-3" padding="none">
          <KeyValueRow
            label="녹음"
            value="요약을 만들기 위해 세션을 녹음합니다"
            className="px-4 py-3.5 sm:px-5"
          />
        </Card>
      </div>

      {note && (
        <div className="mt-10">
          <CardHeader
            title={`${received ? counterpartName : `${counterpartName} 개발자`}가 남긴 말`}
            action={
              <span className="text-[13px] text-fg-subtle">
                계약서와 함께 왔습니다 · {formatDateTimeLabel(note.createdAt)}
              </span>
            }
          />
          <Card variant="surface" className="mt-3">
            <p className="text-sm leading-6 text-fg-muted">{note.content}</p>
          </Card>
        </div>
      )}

      {!signed && !cancelled && (
        <div className="mt-10">
          <p className="text-[13px] text-fg-subtle">
            서명하면 계약서는 더 이상 수정할 수 없습니다.
          </p>
          <div className="mt-3 flex flex-wrap justify-end gap-2">
            {received && !signedByAnyone && (
              <Link
                href={ROUTES.roomContractEdit(roomId)}
                className={buttonClass({ variant: "outline", size: "lg" })}
              >
                수정하기
              </Link>
            )}
            {canCancel(contract, received) && (
              <CancelContractButton
                contractId={contract.contractId}
                counterpartName={counterpartName}
                deposited={mine}
              />
            )}
            {mine ? (
              <p className="self-center text-[13px] text-fg-subtle">
                서명했습니다. {counterpartName}님이 서명하면 계약이 성립합니다.
              </p>
            ) : (
              <SignContractButton
                roomId={roomId}
                contractId={contract.contractId}
                subject={`컨설팅 계약서 · ${roomTitle} · ${amount}원`}
                /*
                  개발 계약은 「예치」라고 적지만 컨설팅은 「결제」입니다. 한 번 만나고
                  끝나는 거래라 돈을 맡겨 두고 단계마다 푸는 구조가 아닙니다.
                  에스크로를 거치는지는 아직 정해지지 않았습니다
                  (`RULES.md` 「미확정」 — 컨설팅 대금의 에스크로 경유 여부).
                */
                label={received ? "서명하고 계약하기" : `서명하고 ${amount}원 결제`}
                confirmLabel={received ? "서명하고 계약" : `서명하고 ${amount}원 결제`}
                depositNext={!received}
              />
            )}
          </div>
        </div>
      )}
    </section>
  );
}
