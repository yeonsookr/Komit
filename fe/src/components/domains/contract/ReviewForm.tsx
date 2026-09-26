"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Field, RatingScale, StickyBar, Textarea } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

/** 리뷰 본문 길이 상한. R-REV-04. */
const MAX_CONTENT_LENGTH = 1000;

/** 두 축의 눈금 문구. 화면에 적힌 것을 그대로 씁니다 (R-REV-01). */
const DEV_LABELS = ["전혀", "조금", "보통", "잘됨", "완전"] as const;
const EXP_LABELS = ["못했음", "조금", "보통", "잘함", "완전"] as const;

export interface ReviewFormProps {
  contractId: number;
  roomId: number;
  /** 머리말 한 줄. 예: `세션 완료 8월 29일 14:32`, `개발 완료 9월 4일 · 정산 완료` */
  note: string;
}

/**
 * ReviewForm — 의뢰인의 리뷰 작성.
 *
 * 개발 실력과 설명력을 나누어 받습니다. 본문은 비워도 되고 점수만 남겨도 됩니다.
 * 리뷰는 다음 사람이 읽는 값이라 계약이 끝난 뒤에만 들어옵니다 (R-REV-02).
 */
export function ReviewForm({
  contractId,
  roomId,
  note,
}: Readonly<ReviewFormProps>) {
  const router = useRouter();
  const showToast = useToast();

  const [devScore, setDevScore] = React.useState(0);
  const [expScore, setExpScore] = React.useState(0);
  const [content, setContent] = React.useState("");
  const [saving, setSaving] = React.useState(false);

  const canSubmit = devScore > 0 && expScore > 0 && !saving;

  const submit = async () => {
    if (!canSubmit) return;
    setSaving(true);

    try {
      await clientApi.post(INTERNAL.CONTRACT_REVIEW(contractId), {
        devScore,
        expScore,
        content: content.trim() === "" ? null : content.trim(),
      });
      showToast({ tone: "success", title: "리뷰를 남겼습니다." });
      router.push(ROUTES.room(roomId));
    } catch (error) {
      showToast({
        tone: "danger",
        title:
          error instanceof ApiError
            ? error.message
            : "리뷰를 남기지 못했습니다.",
      });
      setSaving(false);
    }
  };

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">리뷰 작성</h2>
        <p className="mt-1 text-[13px] text-fg-subtle">
          {note}
        </p>
      </header>

      <div className="mt-8 flex flex-col gap-8">
        <Field label="실력">
          <RatingScale
            name="devScore"
            value={devScore}
            onChange={setDevScore}
            labels={[...DEV_LABELS]}
          />
        </Field>

        <Field label="설명력">
          <RatingScale
            name="expScore"
            value={expScore}
            onChange={setExpScore}
            labels={[...EXP_LABELS]}
          />
        </Field>

        <Field label="리뷰 내용" helper="다음 사람이 읽습니다">
          <Textarea
            rows={5}
            showCount
            maxLength={MAX_CONTENT_LENGTH}
            value={content}
            onChange={(event) => setContent(event.target.value)}
            placeholder="확인 문장대로 해보니 다 됐어요. 결제 실패도 바로 잡아주셨습니다."
          />
        </Field>
      </div>

      <StickyBar className="mt-10">
        <span className="text-sm text-fg-secondary">
          {canSubmit || saving ? (
            <>
              실력 <span className="font-mono tabular-nums">{devScore}</span> ·
              설명력 <span className="font-mono tabular-nums">{expScore}</span>
            </>
          ) : (
            "두 점수를 모두 골라주세요"
          )}
        </span>
        <Button
          size="lg"
          loading={saving}
          disabled={!canSubmit}
          onClick={submit}
        >
          리뷰 남기기
        </Button>
      </StickyBar>
    </section>
  );
}
