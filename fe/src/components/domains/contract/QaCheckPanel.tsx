"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { buttonClass } from "@/components/buttons/buttonStyles";
import {
  Card,
  CardHeader,
  Checkbox,
  KeyValueRow,
  Money,
  StickyBar,
} from "@/components/common";
import {
  Callout,
  Modal,
  ModalFooter,
  ModalHeader,
  useToast,
} from "@/components/feedback";
import { DeliverableFiles } from "./DeliverableFiles";
import { PayoutRefresh } from "./PayoutRefresh";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import {
  AUTO_PASS_DAYS,
  checkedCount,
  confirmedAmount,
  confirmedCount,
  isAwaitingCheck,
  isConfirmed,
  isHandover,
  isReturned,
  milestoneActionFailure,
  nearestAutoPassAt,
  PAYOUT_PENDING_LABEL,
  payoutStage,
  remainingAmount,
  remainingCount,
} from "@/lib/contract";
import { formatDateLabel, formatShortDateTime } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type { DevelopmentAgreement, Milestone } from "@/types/contract";

export interface QaCheckPanelProps {
  roomId: number;
  development: DevelopmentAgreement;
}

/**
 * 이번 체크로 작업의 확인 문장이 모두 채워지는지. 채워지면 통과시키기, 아니면 확인 결과 저장으로
 * 보냅니다 (R-QA-01). 새로 체크한 문장이 없으면 거짓입니다.
 */
export function willPass(
  milestone: Milestone,
  picked: Record<number, boolean>,
): boolean {
  const newly = milestone.qaCriteria.filter(
    (qa) => qa.status !== "RESOLVED" && picked[qa.qaCriterionId] === true,
  ).length;
  return (
    newly > 0 && checkedCount(milestone) + newly === milestone.qaCriteria.length
  );
}

/**
 * 보내기 전에 확인창을 열어야 하는지. 보낼 작업 가운데 인계 작업이 이번에 통과되면 최종 인수 시험
 * 통과라 한 번 더 묻습니다 (R-QA-03, R-QA-06). 일반 작업만 통과되면 묻지 않습니다.
 */
export function needsFinalAcceptanceConfirm(
  targets: Milestone[],
  picked: Record<number, boolean>,
): boolean {
  return targets.some(
    (milestone) =>
      isHandover(milestone) &&
      isAwaitingCheck(milestone) &&
      willPass(milestone, picked),
  );
}

/** 최종 인수 시험 통과 확인창의 본문. 규칙 값은 적지 않습니다. */
export function FinalAcceptanceConfirm() {
  return (
    <div className="flex flex-col gap-2 text-sm leading-6 text-fg-muted">
      {/* R-QA-03, R-QA-06: 통과는 되돌릴 수 없고 인계 자료 등록도 닫힙니다. */}
      <p>
        통과시키면 되돌릴 수 없습니다. 이후에는 인계 자료도 바뀌지 않습니다.
      </p>
      {/* R-PAY-01: 지급을 한 번 요청하고, 지급액과 수수료가 확정돼야 해금 파일이 열립니다. */}
      <p>
        통과하면 계약 총액 지급을 한 번 요청합니다. 지급액과 수수료가 확정된
        뒤에 해금 파일을 받을 수 있습니다.
      </p>
      {/* R-QA-02: 무응답 자동 통과. 일부만 통과시키면 개발자에게 돌아갑니다. */}
      <p>
        답하지 않아도 기한이 지나면 자동으로 통과됩니다. 안 되는 것이 있으면
        되는 문장만 체크해 제출하십시오. 개발자에게 돌아갑니다.
      </p>
    </div>
  );
}

/**
 * QaCheckPanel — 의뢰인의 확인 탭.
 *
 * 확인 문장을 직접 해보고 체크합니다. **체크는 되돌릴 수 없고**, 모두 체크한
 * 작업을 제출하면 그 자리에서 완료로 확정됩니다.
 *
 * 그래서 제출 버튼이 두 API 로 갈립니다.
 *   다 못 채웠으면 — 확인 결과 저장. 개발자가 무엇이 남았는지 봅니다
 *   다 채웠으면   — 작업 통과시키기. 되돌릴 수 없습니다
 *
 * 인계 작업을 통과시키는 것은 최종 인수 시험 통과라 확인창을 한 번 거칩니다. 통과 뒤 지급은 서버가
 * 요청하고, 지급이 확정될 때까지 지급 처리 중으로 보입니다 (R-PAY-01).
 */
export function QaCheckPanel({
  roomId,
  development,
}: Readonly<QaCheckPanelProps>) {
  const router = useRouter();
  const showToast = useToast();

  /** 이번에 새로 체크한 확인 문장. 이미 통과한 것은 여기 담지 않습니다. */
  const [picked, setPicked] = React.useState<Record<number, boolean>>({});
  const [busy, setBusy] = React.useState<number | null>(null);
  /** 확인창을 열고 기다리는 제출. 확인하면 이 작업들을 보냅니다. */
  const [confirming, setConfirming] = React.useState<Milestone[] | null>(null);

  const milestones = development.milestones;
  const autoPassAt = nearestAutoPassAt(milestones);
  const stage = payoutStage(development);

  const toggle = (qaCriterionId: number) =>
    setPicked((prev) => ({ ...prev, [qaCriterionId]: !prev[qaCriterionId] }));

  /** 이번에 체크한 문장이 있어 제출할 수 있는 작업. 확인 요청을 받은 작업만 체크합니다. */
  const pending = milestones.filter(
    (milestone) =>
      isAwaitingCheck(milestone) &&
      milestone.qaCriteria.some((qa) => picked[qa.qaCriterionId] === true),
  );

  /** 작업 하나를 보냅니다. 보냈으면 true 입니다. */
  const submit = async (milestone: Milestone): Promise<boolean> => {
    if (busy !== null) return false;

    const already = milestone.qaCriteria.filter(
      (qa) => qa.status === "RESOLVED",
    );
    const newly = milestone.qaCriteria.filter((qa) => picked[qa.qaCriterionId]);
    if (newly.length === 0) return false;

    // 이번 체크까지 더해 전부 채워지면 완료 확정, 아니면 거기까지 알립니다.
    const willBeFull = willPass(milestone, picked);
    setBusy(milestone.milestoneId);

    try {
      if (willBeFull) {
        await clientApi.post(
          INTERNAL.MILESTONE_CONFIRMATION(milestone.milestoneId),
          {
            qaCriterionIds: [...already, ...newly].map(
              (qa) => qa.qaCriterionId,
            ),
          },
        );
        showToast({
          tone: "success",
          title: `‘${milestone.title}’ 을 통과시켰습니다.`,
        });
      } else {
        await clientApi.put(
          INTERNAL.MILESTONE_QA_RESULTS(milestone.milestoneId),
          {
            qaCriterionIds: newly.map((qa) => qa.qaCriterionId),
          },
        );
        showToast({
          tone: "success",
          title: "확인한 것까지 알렸습니다.",
          description: "개발자가 남은 문장을 보고 고쳐 다시 요청합니다.",
        });
      }
      // 보낸 작업의 체크만 비웁니다. 이어서 보낼 작업의 체크는 남깁니다.
      setPicked((prev) => {
        const next = { ...prev };
        for (const qa of milestone.qaCriteria) delete next[qa.qaCriterionId];
        return next;
      });
      router.refresh();
      return true;
    } catch (error) {
      // 그 사이 작업 상태가 바뀌었거나 서버가 본 체크가 다르면 다시 읽습니다.
      const failure = milestoneActionFailure(error, "보내지 못했습니다.");
      showToast({ tone: "danger", title: failure.title });
      if (failure.refresh) router.refresh();
      return false;
    } finally {
      setBusy(null);
    }
  };

  /** 작업을 순서대로 보냅니다. 하나가 실패하면 거기서 멈춥니다. */
  const submitEach = async (targets: Milestone[]) => {
    for (const milestone of targets) {
      if (!(await submit(milestone))) return;
    }
  };

  /** 카드 버튼과 하단 버튼이 함께 씁니다. 인계 작업이 통과될 때만 확인창을 거칩니다. */
  const request = (targets: Milestone[]) => {
    if (busy !== null) return;
    if (needsFinalAcceptanceConfirm(targets, picked)) {
      setConfirming(targets);
      return;
    }
    void submitEach(targets);
  };

  const confirmPass = async () => {
    if (confirming === null) return;
    await submitEach(confirming);
    setConfirming(null);
  };

  const closeConfirm = () => {
    if (busy === null) setConfirming(null);
  };

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">작업 확인</h2>
        <p className="mt-1.5 text-[13px] leading-6 text-fg-subtle">
          계약할 때 정한 확인 문장이 완료 조건입니다. 직접 해보고 체크하시면 그
          작업이 완료로 확정됩니다.
        </p>
      </header>

      <div className="mt-6">
        <CardHeader
          title={`작업 ${milestones.length}건`}
          action={
            <span className="text-[13px] text-fg-subtle">
              합계{" "}
              <Money
                value={development.totalAmount}
                className="text-fg-secondary"
              />{" "}
              중{" "}
              <Money
                value={confirmedAmount(milestones)}
                className="text-fg-secondary"
              />{" "}
              통과 확정
            </span>
          }
        />

        {stage === "PENDING" && (
          <>
            <PayoutRefresh active />
            <Callout tone="success" title={PAYOUT_PENDING_LABEL} className="mt-3">
              계약 총액 지급을 진행합니다. 지급액과 수수료가 확정되면 계약이
              완료되고 해금 파일을 받을 수 있습니다. 지급이 늦어지면 Komit에
              문의해 주십시오.
            </Callout>
          </>
        )}

        {/* 지급액과 수수료가 모두 확정돼야 완료로 기록되고 해금 파일이 열립니다 (R-PAY-01, R-PAY-07). */}
        {stage === "PAID" && (
          <Callout tone="success" title="지급 완료" className="mt-3">
            지급액과 수수료가 확정돼 계약이 완료됐습니다. 이제 해금 파일을 받을
            수 있습니다.
          </Callout>
        )}

        <Callout tone="warning" className="mt-3">
          확인 문장을 다 체크한 작업은 제출하는 순간 완료로 확정됩니다. 되돌릴
          수 없습니다.
        </Callout>

        <ul className="mt-3 flex flex-col gap-3">
          {milestones.map((milestone) => {
            const confirmed = isConfirmed(milestone);
            const awaiting = isAwaitingCheck(milestone);
            const returned = isReturned(milestone);
            const handover = isHandover(milestone);
            const total = milestone.qaCriteria.length;
            const checked = checkedCount(milestone);
            const newly = milestone.qaCriteria.filter(
              (qa) => picked[qa.qaCriterionId],
            ).length;

            return (
              <li key={milestone.milestoneId}>
                <Card padding="none">
                  <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1 px-4 pt-4 sm:px-5">
                    <p className="min-w-0 flex-1 text-sm font-semibold text-fg">
                      {milestone.title}
                    </p>
                    {!handover && (
                      <Money
                        value={milestone.price}
                        className="text-sm text-fg-secondary"
                      />
                    )}
                    <span className="font-mono text-[13px] text-fg-subtle tabular-nums">
                      {checked} / {total} 확인
                    </span>
                  </div>

                  {/* 넘겨받을 파일. 확인 문장과 짝이 아니라 따로 셉니다. 시험 중에는 펼쳐 둡니다. */}
                  {handover && (
                    <DeliverableFiles
                      deliverables={development.deliverables}
                      received={false}
                      paid={stage === "PAID"}
                      defaultOpen={awaiting || undefined}
                    />
                  )}

                  {handover && awaiting && (
                    <p className="mt-3 px-4 text-[13px] leading-6 text-fg-muted sm:px-5">
                      최종 인수 시험 중입니다. 위 인계 자료로 아래 확인 문장을
                      직접 해보고 체크하십시오.
                    </p>
                  )}

                  {/* R-QA-02: 일부만 통과시키면 개발자에게 돌아가고 자동 통과가 멈춥니다. */}
                  {returned && (
                    <p className="mt-3 px-4 text-[13px] leading-6 text-fg-muted sm:px-5">
                      일부만 통과시켜 개발자에게 돌려보냈습니다. 개발자가 고쳐
                      다시 요청할 때까지 자동 통과가 멈춥니다.
                    </p>
                  )}

                  <ul className="mt-3 flex flex-col gap-2 px-4 sm:px-5">
                    {milestone.qaCriteria.map((qa) => {
                      const passed = qa.status === "RESOLVED";

                      return (
                        <li key={qa.qaCriterionId}>
                          <Checkbox
                            label={qa.testCase ?? ""}
                            checked={
                              passed || picked[qa.qaCriterionId] === true
                            }
                            // 통과한 문장은 되돌릴 수 없어 잠급니다 (R-QA-03).
                            // 확인 요청을 받기 전에는 체크하지 않습니다.
                            locked={passed}
                            disabled={passed || !awaiting}
                            onChange={() => toggle(qa.qaCriterionId)}
                          />
                        </li>
                      );
                    })}
                  </ul>

                  <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-line-subtle px-4 py-3 sm:px-5">
                    <span className="font-mono text-[13px] text-fg-subtle tabular-nums">
                      {confirmed && milestone.confirmedAt ? (
                        <>
                          {formatShortDateTime(milestone.confirmedAt)} 통과
                          {!handover && (
                            <>
                              {" · "}
                              <Money
                                value={milestone.price}
                                className="text-fg-subtle"
                              />{" "}
                              확정
                            </>
                          )}
                        </>
                      ) : milestone.submittedAt ? (
                        <>
                          {`${formatShortDateTime(milestone.submittedAt)} 확인 요청 받음`}
                          {/* 자동 통과 시각은 서버가 셉니다 (R-QA-02). */}
                          {awaiting &&
                            milestone.autoPassAt &&
                            ` · ${formatShortDateTime(milestone.autoPassAt)} 자동 통과`}
                        </>
                      ) : (
                        "확인 요청을 받으면 체크할 수 있습니다"
                      )}
                    </span>
                    {awaiting && (
                      <Button
                        size="sm"
                        disabled={newly === 0}
                        loading={busy === milestone.milestoneId}
                        onClick={() => request([milestone])}
                      >
                        {checked + newly === total
                          ? "제출하고 통과시키기"
                          : "확인된 기능 제출"}
                      </Button>
                    )}
                  </div>
                </Card>
              </li>
            );
          })}
        </ul>
      </div>

      <div className="mt-10">
        <CardHeader
          title="예치금 내역"
          action={
            <span className="flex items-center gap-3">
              <span className="text-[13px] text-fg-subtle">
                <span className="font-mono tabular-nums">
                  {milestones.length}
                </span>
                건 모두 통과하면 한 번에 지급 요청
              </span>
              <Link
                href={ROUTES.settlements}
                className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                원장 보기
              </Link>
            </span>
          }
        />
        <Card className="mt-3" padding="none">
          <div className="divide-y divide-line-subtle">
            <KeyValueRow
              labelWidth={150}
              label="예치한 돈"
              value={<Money value={development.totalAmount} />}
              className="px-4 py-3.5 sm:px-5"
            />
            <KeyValueRow
              labelWidth={150}
              label={`통과 ${confirmedCount(milestones)}건 · ${milestones.length}건 중`}
              value={<Money value={confirmedAmount(milestones)} />}
              className="px-4 py-3.5 sm:px-5"
            />
            <KeyValueRow
              labelWidth={150}
              label={`확인 대기 ${remainingCount(milestones)}건`}
              value={<Money value={remainingAmount(milestones)} />}
              className="px-4 py-3.5 sm:px-5"
            />
            {/* 지급이 끝나면 예치금에서 계약 총액이 나갔으므로 잔액 대신 지급된 돈을 보입니다 (R-PAY-01). */}
            <KeyValueRow
              labelWidth={150}
              label={stage === "PAID" ? "지급된 돈" : "예치금 잔액"}
              value={<Money value={development.totalAmount} />}
              className="px-4 py-3.5 sm:px-5"
            />
          </div>
        </Card>
        <p className="mt-2 text-xs leading-5 text-fg-subtle">
          {stage === "PAID"
            ? "예치금에서 계약 총액이 지급됐습니다. 예치금은 결제대행사 에스크로 계정에 있었고, Komit은 이 돈을 갖고 있지 않습니다."
            : "예치금은 결제대행사 에스크로 계정에 있습니다. Komit은 이 돈을 갖고 있지 않습니다."}
        </p>
      </div>

      <div className="mt-8 flex flex-col gap-3">
        <Callout
          tone="neutral"
          title="확인이 어려우면"
          action={
            <Link
              href={ROUTES.room(roomId)}
              className={buttonClass({ variant: "outline", size: "sm" })}
            >
              무료로 물어보기
            </Link>
          }
        >
          확인 문장이 무슨 뜻인지 몰라도 됩니다. 채팅으로 풀어드립니다.
        </Callout>

        <Callout
          tone="neutral"
          title="답이 없으면"
          action={
            <Link
              href={ROUTES.roomReport(roomId)}
              className={buttonClass({ variant: "danger", size: "sm" })}
            >
              문제 신고
            </Link>
          }
        >
          대화로 요청했는데 {AUTO_PASS_DAYS}일 넘게 답이 없거나 말이 안 통하면
          Komit을 부르십시오.
        </Callout>
      </div>

      <StickyBar className="mt-10 flex-col items-stretch gap-1.5">
        <span className="flex items-center justify-between gap-4">
          <span className="text-sm text-fg-secondary">
            남은 작업{" "}
            <span className="font-mono tabular-nums">
              {remainingCount(milestones)}
            </span>
            건{" · "}
            <Money value={remainingAmount(milestones)} className="text-fg" />
          </span>
          <span className="flex shrink-0 items-center gap-3">
            {autoPassAt && (
              <span className="font-mono text-[13px] text-fg-subtle tabular-nums">
                {formatDateLabel(autoPassAt)} 자동 통과
              </span>
            )}
            <Button
              disabled={pending.length === 0}
              loading={busy !== null}
              onClick={() => request(pending)}
            >
              확인된 기능 제출
            </Button>
          </span>
        </span>
        <span className="text-[13px] text-fg-subtle">
          체크한 확인 문장이 있는 작업을 제출할 수 있습니다. 지금은{" "}
          <span className="font-mono tabular-nums">{pending.length}</span>
          건입니다. 확인하지 않은 작업의 대금은 예치금에 그대로 있습니다.
        </span>
      </StickyBar>

      <Modal
        open={confirming !== null}
        onClose={closeConfirm}
        aria-labelledby="final-acceptance-confirm-title"
      >
        <ModalHeader
          id="final-acceptance-confirm-title"
          title="최종 인수 시험을 통과시킬까요?"
        />
        <FinalAcceptanceConfirm />
        <ModalFooter>
          <Button
            variant="outline"
            onClick={closeConfirm}
            disabled={busy !== null}
          >
            돌아가기
          </Button>
          <Button onClick={confirmPass} loading={busy !== null}>
            통과시키기
          </Button>
        </ModalFooter>
      </Modal>
    </section>
  );
}
