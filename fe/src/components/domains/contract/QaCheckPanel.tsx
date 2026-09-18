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
import { Callout, useToast } from "@/components/feedback";
import { DeliverableFiles } from "./DeliverableFiles";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import {
  AUTO_PASS_DAYS,
  checkedCount,
  confirmedAmount,
  confirmedCount,
  isConfirmed,
  isHandover,
  nearestAutoPassAt,
  remainingAmount,
  remainingCount,
} from "@/lib/contract";
import { formatDateLabel, formatShortDateTime } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { DevelopmentAgreement, Milestone } from "@/types/contract";

export interface QaCheckPanelProps {
  roomId: number;
  development: DevelopmentAgreement;
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

  const milestones = development.milestones;
  const autoPassAt = nearestAutoPassAt(milestones);

  const toggle = (qaCriterionId: number) =>
    setPicked((prev) => ({ ...prev, [qaCriterionId]: !prev[qaCriterionId] }));

  /** 이번에 체크한 문장이 있어 제출할 수 있는 작업. */
  const pending = milestones.filter(
    (milestone) =>
      !isConfirmed(milestone) &&
      milestone.qaCriteria.some((qa) => picked[qa.qaCriterionId] === true),
  );

  const submit = async (milestone: Milestone) => {
    if (busy !== null) return;

    const already = milestone.qaCriteria.filter(
      (qa) => qa.status === "RESOLVED",
    );
    const newly = milestone.qaCriteria.filter((qa) => picked[qa.qaCriterionId]);
    if (newly.length === 0) return;

    // 이번 체크까지 더해 전부 채워지면 완료 확정, 아니면 거기까지 알립니다.
    const willBeFull =
      already.length + newly.length === milestone.qaCriteria.length;
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
      setPicked({});
      router.refresh();
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "보내지 못했습니다.",
      });
    } finally {
      setBusy(null);
    }
  };

  /** 체크한 작업을 순서대로 제출합니다. 하나가 실패하면 거기서 멈춥니다. */
  const submitAll = async () => {
    for (const milestone of pending) {
      await submit(milestone);
    }
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

        <Callout tone="warning" className="mt-3">
          확인 문장을 다 체크한 작업은 제출하는 순간 완료로 확정됩니다. 되돌릴
          수 없습니다.
        </Callout>

        <ul className="mt-3 flex flex-col gap-3">
          {milestones.map((milestone) => {
            const confirmed = isConfirmed(milestone);
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
                    {!isHandover(milestone) && (
                      <Money
                        value={milestone.price}
                        className="text-sm text-fg-secondary"
                      />
                    )}
                    <span className="font-mono text-[13px] text-fg-subtle tabular-nums">
                      {checked} / {total} 확인
                    </span>
                  </div>

                  {/* 넘겨받을 파일. 확인 문장과 짝이 아니라 따로 셉니다. */}
                  {isHandover(milestone) && (
                    <DeliverableFiles
                      deliverables={development.deliverables}
                      received={false}
                    />
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
                            locked={passed}
                            disabled={passed || confirmed}
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
                          {!isHandover(milestone) && (
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
                        `${formatShortDateTime(milestone.submittedAt)} 확인 요청 받음`
                      ) : null}
                    </span>
                    {!confirmed && (
                      <Button
                        size="sm"
                        disabled={newly === 0}
                        loading={busy === milestone.milestoneId}
                        onClick={() => submit(milestone)}
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
                건 모두 통과하면 지급
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
            <KeyValueRow
              labelWidth={150}
              label="예치금 잔액"
              value={<Money value={development.totalAmount} />}
              className="px-4 py-3.5 sm:px-5"
            />
          </div>
        </Card>
        <p className="mt-2 text-xs leading-5 text-fg-subtle">
          예치금은 결제대행사 에스크로 계정에 있습니다. Komit은 이 돈을 갖고
          있지 않습니다.
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
              onClick={submitAll}
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
    </section>
  );
}
