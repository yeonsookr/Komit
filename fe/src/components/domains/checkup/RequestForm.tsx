"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Card, Mono } from "@/components/common";
import { EmptyState, useToast } from "@/components/feedback";
import { buttonClass } from "@/components/buttons/buttonStyles";
import { RequirementList } from "./RequirementList";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { isInRequest, isResolved } from "@/lib/diagnosisReport";
import { ApiError } from "@/types/api";
import type { Diagnosis, Issue } from "@/types/diagnosis";
import type { RecommendationBatch } from "@/types/recommendation";

export interface RequestFormProps {
  diagnosis: Diagnosis;
  issues: Issue[];
}

/**
 * RequestForm — 개발자를 찾기 전에 요청서를 확인합니다.
 *
 * 요청서는 따로 저장되는 문서가 아니라 고른 요구사항들을 모아 부르는 이름입니다
 * (목 명세). 결함 목록 중 SUGGESTED·CONTRACTED만 요청서 본문으로 그립니다.
 * 직접 해결한 요구사항은 요청서에서 빠진 것으로 아래에 따로 보여줍니다.
 */
export function RequestForm({ diagnosis, issues }: Readonly<RequestFormProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [submitting, setSubmitting] = React.useState(false);

  // R-REQ: 고른 이슈만 요청서로 이어집니다. 아직 고르지 않은 UNRESOLVED는 숨깁니다.
  const requirements = issues.filter(isInRequest);
  const excluded = issues.filter(isResolved);

  /** 요청서의 요구사항으로 추천 회차를 만들고 그 화면으로 갑니다. */
  const findDevelopers = async () => {
    if (requirements.length === 0 || submitting) return;
    setSubmitting(true);

    try {
      const { data } = await clientApi.post<RecommendationBatch>(
        INTERNAL.RECOMMENDATIONS(diagnosis.project.projectId),
        {
          diagnosisId: diagnosis.diagnosisId,
          issueIds: requirements.map((issue) => issue.issueId),
        },
      );
      router.push(ROUTES.recommendation(data.batchId));
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "개발자를 찾지 못했습니다.",
      });
      setSubmitting(false);
    }
  };

  return (
    <>
      <header>
        <h1 className="text-[28px] leading-tight font-bold text-fg">요청서</h1>
        <p className="mt-1.5 text-[13px] text-fg-subtle">
          {diagnosis.project.name} · 요구사항{" "}
          <span className="font-mono tabular-nums">{requirements.length}</span>건
        </p>
      </header>

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
          emptyDescription="요구사항을 다시 골라 주세요."
        />
      </section>

      {excluded.length > 0 && (
        <section className="mt-10">
          <div className="flex items-baseline justify-between gap-4">
            <h2 className="text-base font-bold text-fg">요청서에서 제외한 것</h2>
            <p className="text-[13px] text-fg-subtle">
              <span className="font-mono tabular-nums">{excluded.length}</span>건
            </p>
          </div>

          <Card variant="outline" padding="none" className="mt-3">
            <ul>
              {excluded.map((issue) => (
                <li
                  key={issue.issueId}
                  className="flex items-center gap-4 px-5 py-4 [&:not(:first-child)]:border-t [&:not(:first-child)]:border-line-subtle"
                >
                  <p className="min-w-0 flex-1 truncate text-sm text-fg-muted">{issue.title}</p>
                  <span className="shrink-0 text-[13px] text-fg-subtle">직접 처리</span>
                </li>
              ))}
            </ul>
          </Card>
        </section>
      )}

      <div className="mt-10 flex flex-wrap justify-end gap-3">
        <Link
          href={ROUTES.checkupRequestEdit(diagnosis.diagnosisId)}
          className={buttonClass({ variant: "outline", size: "lg" })}
        >
          요청서 수정하기
        </Link>
        <Button
          size="lg"
          onClick={findDevelopers}
          loading={submitting}
          disabled={requirements.length === 0}
        >
          개발자 찾기
        </Button>
      </div>
    </>
  );
}
