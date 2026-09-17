"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { buttonClass } from "@/components/buttons/buttonStyles";
import {
  Card,
  CardHeader,
  KeyValueRow,
  Money,
  StickyBar,
} from "@/components/common";
import { Callout, useToast } from "@/components/feedback";
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
import type {
  Deliverable,
  DevelopmentAgreement,
  Milestone,
} from "@/types/contract";

export interface QaSubmitPanelProps {
  roomId: number;
  development: DevelopmentAgreement;
  /** 상대(의뢰인) 이름. 안내 문구에 씁니다. */
  counterpartName: string;
}

/**
 * QaSubmitPanel — 개발자의 확인 탭.
 *
 * 의뢰인이 어디까지 확인했는지 보고, 끝난 작업마다 확인 요청을 보냅니다.
 * 통과 판정은 의뢰인이 하므로 여기서는 체크할 수 없습니다.
 *
 * 인계물은 파일을 올려야 의뢰인이 받아 확인할 수 있습니다 (R-QA-04).
 */
export function QaSubmitPanel({
  roomId,
  development,
  counterpartName,
}: Readonly<QaSubmitPanelProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [busy, setBusy] = React.useState<number | null>(null);

  const milestones = development.milestones;
  const autoPassAt = nearestAutoPassAt(milestones);
  /** 인계 작업. 인계물을 다 올린 뒤 이 작업으로 확인 요청을 보냅니다. */

  const requestCheck = async (milestone: Milestone) => {
    if (busy !== null) return;
    setBusy(milestone.milestoneId);

    try {
      await clientApi.post(
        INTERNAL.MILESTONE_SUBMISSION(milestone.milestoneId),
        {},
      );
      showToast({
        tone: "success",
        title: "확인 요청을 보냈습니다.",
        description: `${counterpartName}님이 확인 문장을 눌러 통과시킵니다.`,
      });
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

  const upload = async (deliverable: Deliverable, file: File) => {
    if (busy !== null) return;
    setBusy(deliverable.deliverableId);

    const form = new FormData();
    form.append("file", file);

    try {
      await clientApi.post(
        INTERNAL.DELIVERABLE_FILE(deliverable.deliverableId),
        form,
      );
      showToast({
        tone: "success",
        title: `‘${deliverable.title}’ 을 올렸습니다.`,
      });
      router.refresh();
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "올리지 못했습니다.",
      });
    } finally {
      setBusy(null);
    }
  };

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">작업 확인</h2>
        <p className="mt-1.5 text-[13px] leading-6 text-fg-subtle">
          계약할 때 정한 확인 문장이 완료 조건입니다. {milestones.length}건을
          모두 통과시키면 대금이 한 번에 나갑니다.
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

        <ul className="mt-3 flex flex-col gap-3">
          {milestones.map((milestone) => {
            const confirmed = isConfirmed(milestone);

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
                      확인 {checkedCount(milestone)} /{" "}
                      {milestone.qaCriteria.length}
                    </span>
                  </div>

                  <ul className="mt-2 flex flex-col gap-1 px-4 sm:px-5">
                    {milestone.qaCriteria.map((qa, index) => {
                      // 인계 작업의 문장은 넘겨줄 것 하나씩과 짝입니다. 그 줄에서 바로 올립니다.
                      const deliverable = isHandover(milestone)
                        ? development.deliverables[index]
                        : undefined;

                      return (
                        <li
                          key={qa.qaCriterionId}
                          className="flex items-center gap-3 text-[13px] leading-6"
                        >
                          <span
                            className={
                              qa.status === "RESOLVED"
                                ? "min-w-0 flex-1 text-fg-subtle line-through"
                                : "min-w-0 flex-1 text-fg-muted"
                            }
                          >
                            {qa.testCase}
                          </span>
                          {deliverable && (
                            <>
                              {deliverable.deliveredAt && (
                                <span className="shrink-0 font-mono text-fg-subtle tabular-nums">
                                  {formatShortDateTime(deliverable.deliveredAt)}{" "}
                                  올림
                                </span>
                              )}
                              <label className="shrink-0 cursor-pointer font-semibold text-primary hover:underline">
                                {deliverable.fileId === null
                                  ? "파일 첨부"
                                  : "다시 올리기"}
                                <input
                                  type="file"
                                  className="sr-only"
                                  disabled={busy !== null}
                                  onChange={(event) => {
                                    const file = event.target.files?.[0];
                                    if (file) void upload(deliverable, file);
                                    event.target.value = "";
                                  }}
                                />
                              </label>
                            </>
                          )}
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
                        `${formatShortDateTime(milestone.submittedAt)} 확인 요청 보냄`
                      ) : null}
                    </span>
                    {!confirmed && (
                      <Button
                        size="sm"
                        variant={milestone.submittedAt ? "outline" : "primary"}
                        loading={busy === milestone.milestoneId}
                        onClick={() => requestCheck(milestone)}
                      >
                        {isHandover(milestone)
                          ? "인계하고 확인 요청 보내기"
                          : milestone.submittedAt
                            ? "확인 요청 다시 보내기"
                            : "확인 요청 보내기"}
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
          title="통과 현황"
          action={
            <span className="flex items-center gap-3">
              <span className="text-[13px] text-fg-subtle">
                <span className="font-mono tabular-nums">
                  {milestones.length}
                </span>
                건 모두 통과하면 한 번에 지급
              </span>
              <Link
                href={ROUTES.settlements}
                className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                정산 보기
              </Link>
            </span>
          }
        />
        <Card className="mt-3" padding="none">
          <div className="divide-y divide-line-subtle">
            <KeyValueRow
              labelWidth={170}
              label="의뢰인이 예치한 돈"
              value={<Money value={development.totalAmount} />}
              className="px-4 py-3.5 sm:px-5"
            />
            <KeyValueRow
              labelWidth={170}
              label={`통과 ${confirmedCount(milestones)}건 · ${milestones.length}건 중`}
              value={<Money value={confirmedAmount(milestones)} />}
              className="px-4 py-3.5 sm:px-5"
            />
            <KeyValueRow
              labelWidth={170}
              label={`확인 대기 ${remainingCount(milestones)}건 · 아직 못 받음`}
              value={<Money value={remainingAmount(milestones)} />}
              className="px-4 py-3.5 sm:px-5"
            />
            <KeyValueRow
              labelWidth={170}
              label="소득 내역"
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
          title="확인이 안 될 때"
          action={
            <Link
              href={ROUTES.room(roomId)}
              className={buttonClass({ variant: "outline", size: "sm" })}
            >
              대화로 안내하기
            </Link>
          }
        >
          의뢰인이 확인하는 방법을 몰라 멈춰 있을 수 있습니다. 어떻게 확인하면
          되는지 대화에서 알려 주십시오.
        </Callout>

        <Callout
          tone="neutral"
          title="답이 없을 때"
          action={
            <Link
              href={ROUTES.roomReport(roomId)}
              className={buttonClass({ variant: "danger", size: "sm" })}
            >
              문제 신고
            </Link>
          }
        >
          답이 없어도{" "}
          {autoPassAt
            ? `${formatDateLabel(autoPassAt)}에`
            : `${AUTO_PASS_DAYS}일 뒤`}{" "}
          자동 통과됩니다. 그전에 정리가 필요하면 Komit이 요청서와 확인 문장,
          대화 기록을 사실대로 양쪽에 보여 드립니다.
        </Callout>
      </div>

      <StickyBar className="mt-10">
        <span className="text-sm text-fg-secondary">
          남은 작업{" "}
          <span className="font-mono tabular-nums">
            {remainingCount(milestones)}
          </span>
          건{" · "}
          <Money value={remainingAmount(milestones)} className="text-fg" />
        </span>
        {autoPassAt && (
          <span className="font-mono text-[13px] text-fg-subtle tabular-nums">
            {formatDateLabel(autoPassAt)} 자동 통과
          </span>
        )}
      </StickyBar>
    </section>
  );
}
