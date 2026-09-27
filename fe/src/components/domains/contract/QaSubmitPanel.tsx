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
import { busyKey, DeliverableFiles } from "./DeliverableFiles";
import { clientApi } from "@/lib/api/client";
import { uploadFromBrowser } from "@/lib/upload/browserUpload";
import { INTERNAL } from "@/lib/api/endpoints";
import {
  AUTO_PASS_DAYS,
  checkedCount,
  confirmedAmount,
  confirmedCount,
  handoverRegistration,
  isConfirmed,
  isHandover,
  nearestAutoPassAt,
  registrationFailure,
  remainingAmount,
  remainingCount,
  type RegistrationFailure,
} from "@/lib/contract";
import { formatDateLabel, formatShortDateTime } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type {
  Deliverable,
  DeliverableAccessStage,
  DevelopmentAgreement,
  Milestone,
} from "@/types/contract";

/** 올렸지만 산출물에 붙이지 못한 파일. 다시 붙일 때 그대로 보냅니다. */
interface UnattachedUpload {
  deliverableId: number;
  /** 산출물 이름. 알림에 씁니다. */
  title: string;
  fileId: number;
  fileName: string;
  accessStage: DeliverableAccessStage;
}

export interface QaSubmitPanelProps {
  roomId: number;
  /** 계약 식별자. 계약 뒤 산출물 항목을 더할 때 씁니다. */
  contractId: number;
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
 * 인계물은 파일을 올려야 의뢰인이 받아 확인할 수 있습니다 (R-QA-04). 인계 자료는 일반 작업을 모두
 * 통과한 뒤부터 인계 작업의 확인을 요청하기 전까지 등록합니다 (R-QA-06).
 */
export function QaSubmitPanel({
  roomId,
  contractId,
  development,
  counterpartName,
}: Readonly<QaSubmitPanelProps>) {
  const router = useRouter();
  const showToast = useToast();
  /** 진행 중인 요청의 열쇠(`busyKey`). 한 번에 하나만 보냅니다. */
  const [busy, setBusy] = React.useState<string | null>(null);
  const [unattached, setUnattached] = React.useState<UnattachedUpload | null>(null);

  const milestones = development.milestones;
  const autoPassAt = nearestAutoPassAt(milestones);
  const registration = handoverRegistration(milestones);

  /** 등록 실패를 알립니다. 409 면 그 사이 바뀐 계약을 다시 읽어 닫힌 까닭이 보이게 합니다. */
  const notify = (failure: RegistrationFailure) => {
    showToast({ tone: "danger", title: failure.title });
    if (failure.refresh) router.refresh();
  };

  const requestCheck = async (milestone: Milestone) => {
    if (busy !== null) return;
    setBusy(busyKey.milestone(milestone.milestoneId));

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

  /**
   * 올린 파일을 산출물에 붙입니다. 붙이기만 실패하고 다시 해 볼 만하면 파일 식별자를 두어, 파일을 다시
   * 올리지 않고 붙이기만 다시 합니다. 서버는 같은 파일·용도를 다시 받으면 새로 만들지 않습니다.
   */
  const attach = async (target: UnattachedUpload) => {
    try {
      await clientApi.post(INTERNAL.DELIVERABLE_FILE(target.deliverableId), {
        fileId: target.fileId,
        accessStage: target.accessStage,
      });
      setUnattached(null);
      showToast({
        tone: "success",
        title: `‘${target.title}’ 에 파일을 붙였습니다.`,
      });
      router.refresh();
    } catch (error) {
      const failure = registrationFailure(error, "파일을 붙이지 못했습니다.");
      setUnattached(failure.retry ? target : null);
      notify(failure);
    }
  };

  const upload = async (deliverable: Deliverable, file: File, accessStage: DeliverableAccessStage) => {
    if (busy !== null) return;
    setBusy(busyKey.file(deliverable.deliverableId));

    try {
      const uploaded = await uploadFromBrowser(file, "DELIVERABLE");
      await attach({
        deliverableId: deliverable.deliverableId,
        title: deliverable.title,
        fileId: uploaded.fileId,
        fileName: file.name,
        accessStage,
      });
    } catch (error) {
      // 올리기부터 실패했습니다. 붙일 파일이 없으니 다시 붙이기를 보이지 않습니다.
      notify(registrationFailure(error, "올리지 못했습니다."));
    } finally {
      setBusy(null);
    }
  };

  const retryAttach = async () => {
    if (busy !== null || unattached === null) return;
    setBusy(busyKey.file(unattached.deliverableId));

    try {
      await attach(unattached);
    } finally {
      setBusy(null);
    }
  };

  /**
   * 인수 시험 URL·산출물 항목을 더합니다. 주소 형식처럼 필드 오류면 그 사유를 보여 줍니다.
   * 성공했는지 돌려주어, 실패하면 적은 값을 칸에 남깁니다.
   */
  const add = async (key: string, path: string, body: unknown, done: string): Promise<boolean> => {
    if (busy !== null) return false;
    setBusy(key);

    try {
      await clientApi.post(path, body);
      showToast({ tone: "success", title: done });
      router.refresh();
      return true;
    } catch (error) {
      notify(registrationFailure(error, "더하지 못했습니다."));
      return false;
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

                  {/* 넘겨줄 자료. 확인 문장과 짝이 아니라 따로 셉니다. */}
                  {isHandover(milestone) && (
                    <DeliverableFiles
                      deliverables={development.deliverables}
                      received
                      paid={development.status === "COMPLETED"}
                      registration={registration}
                      busy={busy}
                      contractId={contractId}
                      unattached={unattached}
                      onRetryAttach={() => void retryAttach()}
                      onPick={(deliverable, file, stage) => void upload(deliverable, file, stage)}
                      onAddUrl={(deliverable, url) =>
                        add(
                          busyKey.url(deliverable.deliverableId),
                          INTERNAL.DELIVERABLE_URLS(deliverable.deliverableId),
                          { url },
                          `‘${deliverable.title}’ 에 인수 시험 URL 을 더했습니다.`,
                        )
                      }
                      onAddDeliverable={(title) =>
                        add(
                          busyKey.deliverable(contractId),
                          INTERNAL.CONTRACT_DELIVERABLES(contractId),
                          { title },
                          `산출물 ‘${title}’ 을 더했습니다.`,
                        )
                      }
                    />
                  )}

                  <ul className="mt-2 flex flex-col gap-1 px-4 sm:px-5">
                    {milestone.qaCriteria.map((qa) => (
                      <li
                        key={qa.qaCriterionId}
                        className={
                          qa.status === "RESOLVED"
                            ? "text-[13px] leading-6 text-fg-subtle line-through"
                            : "text-[13px] leading-6 text-fg-muted"
                        }
                      >
                        {qa.testCase}
                      </li>
                    ))}
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
                        loading={busy === busyKey.milestone(milestone.milestoneId)}
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
