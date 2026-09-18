"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button, buttonClass } from "@/components/buttons";
import { Card, Field, Textarea } from "@/components/common";
import { Callout, useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

const MAX_REASON_LENGTH = 255;

export interface ReportFormProps {
  roomId: number;
  contractId: number;
}

export function ReportForm({ roomId, contractId }: Readonly<ReportFormProps>) {
  const router = useRouter();
  const showToast = useToast();
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const [reason, setReason] = React.useState("");
  const [file, setFile] = React.useState<File | null>(null);
  const [reasonError, setReasonError] = React.useState<string | null>(null);
  const [submitting, setSubmitting] = React.useState(false);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = reason.trim();
    if (trimmed === "") {
      setReasonError("신고 내용을 입력해 주세요.");
      return;
    }

    setReasonError(null);
    setSubmitting(true);
    const body = new FormData();
    body.set("reason", trimmed);
    if (file !== null) body.set("file", file, file.name);

    try {
      await clientApi.post(INTERNAL.CONTRACT_REPORTS(contractId), body);
      showToast({ tone: "success", title: "신고가 접수되었습니다." });
      router.push(ROUTES.roomProgress(roomId));
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "신고를 접수하지 못했습니다.",
      });
      setSubmitting(false);
    }
  }

  function clearFile() {
    setFile(null);
    if (fileInputRef.current !== null) fileInputRef.current.value = "";
  }

  return (
    <form className="mt-6 space-y-5" onSubmit={submit}>
      <Callout tone="info" title="계약 진행 중 문제가 생겼나요?">
        이 계약에서 발생한 일을 적어주시면 운영자가 신고 내용을 확인합니다.
      </Callout>

      <Card padding="lg">
        <Field
          label="신고 내용을 적어주세요"
          htmlFor="report-reason"
          required
          error={reasonError}
        >
          <Textarea
            id="report-reason"
            value={reason}
            onChange={(event) => {
              setReason(event.target.value);
              if (reasonError !== null) setReasonError(null);
            }}
            placeholder="예) 8월 31일에 작업 진행 상황을 문의했지만 아직 답변을 받지 못했습니다."
            maxLength={MAX_REASON_LENGTH}
            showCount
            tone="surface"
            disabled={submitting}
            className="min-h-32"
          />
        </Field>
      </Card>

      <Card className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-sm font-semibold text-fg">첨부 (선택)</p>
          <p className="mt-1 text-[13px] text-fg-muted">
            증거 자료가 있다면 파일 1개를 첨부할 수 있습니다.
          </p>
          {file !== null && <p className="mt-2 text-[13px] text-fg">{file.name}</p>}
        </div>
        <div className="flex shrink-0 gap-2">
          {file !== null && (
            <Button variant="ghost" size="sm" disabled={submitting} onClick={clearFile}>
              선택 취소
            </Button>
          )}
          <label className={buttonClass({ variant: "outline", size: "sm" })}>
            파일 고르기
            <input
              ref={fileInputRef}
              className="sr-only"
              type="file"
              disabled={submitting}
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </label>
        </div>
      </Card>

      <Callout tone="neutral" title="Komit은 누가 옳은지 정하지 않습니다.">
        운영자가 신고 내용을 확인한 뒤 접수 상태를 종결 처리합니다.
      </Callout>

      <Button type="submit" variant="danger" size="lg" fullWidth loading={submitting}>
        신고하기
      </Button>
    </form>
  );
}
