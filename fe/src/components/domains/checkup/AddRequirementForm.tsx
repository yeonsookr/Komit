"use client";

import * as React from "react";
import { Button } from "@/components/buttons";
import { Field, Input, NativeSelect } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { SEVERITY_LABEL } from "@/lib/diagnosisReport";
import { ApiError } from "@/types/api";
import type { CodeItem } from "@/types/developer";
import type { Issue, IssueSeverity } from "@/types/diagnosis";

const SEVERITIES: IssueSeverity[] = ["HIGH", "MEDIUM", "LOW"];

export interface AddRequirementFormProps {
  diagnosisId: number;
  /** 분류 선택지. `GET /api/codes/ISSUE_CATEGORY` 로 받아 서버에서 내려줍니다. */
  categories: CodeItem[];
  onAdded: (issue: Issue) => void;
}

/**
 * AddRequirementForm — 검사에서 찾지 못한 요구사항을 직접 적습니다.
 *
 * 평소에는 「+ 요구사항 추가」 한 줄이고, 누르면 입력칸이 열립니다.
 */
export function AddRequirementForm({
  diagnosisId,
  categories,
  onAdded,
}: Readonly<AddRequirementFormProps>) {
  const showToast = useToast();

  const [open, setOpen] = React.useState(false);
  const [title, setTitle] = React.useState("");
  const [categoryCodeId, setCategoryCodeId] = React.useState<number | null>(null);
  const [severity, setSeverity] = React.useState<IssueSeverity>("MEDIUM");
  const [saving, setSaving] = React.useState(false);

  // 분류 선택지가 오면 첫 항목을 기본으로 둡니다.
  React.useEffect(() => {
    setCategoryCodeId((prev) => prev ?? categories[0]?.codeId ?? null);
  }, [categories]);

  const close = () => {
    setOpen(false);
    setTitle("");
    setSeverity("MEDIUM");
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (saving || title.trim().length === 0 || categoryCodeId === null) return;

    setSaving(true);
    try {
      const { data } = await clientApi.post<Issue>(INTERNAL.ISSUES(diagnosisId), {
        title: title.trim(),
        categoryCodeId,
        severity,
      });

      onAdded(data);
      showToast({ tone: "success", title: "요구사항을 추가했습니다." });
      close();
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "요구사항을 추가하지 못했습니다.",
      });
    } finally {
      setSaving(false);
    }
  };

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="mt-4 rounded-sm text-sm font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        + 요구사항 추가
      </button>
    );
  }

  return (
    <form onSubmit={submit} className="mt-4 rounded-xl border border-line px-4 py-4">
      <Field label="무엇을 맡기고 싶으신가요?">
        <Input
          autoFocus
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="예) 주문 취소 화면 추가"
          maxLength={200}
        />
      </Field>

      <div className="mt-3 flex flex-wrap items-end gap-3">
        <Field label="분류" className="min-w-40 flex-1">
          <NativeSelect
            value={categoryCodeId ?? ""}
            onChange={(event) => setCategoryCodeId(Number(event.target.value))}
          >
            {categories.map((category) => (
              <option key={category.codeId} value={category.codeId}>
                {category.codeName}
              </option>
            ))}
          </NativeSelect>
        </Field>

        <Field label="위험도" className="min-w-32 flex-1">
          <NativeSelect
            value={severity}
            onChange={(event) => setSeverity(event.target.value as IssueSeverity)}
          >
            {SEVERITIES.map((value) => (
              <option key={value} value={value}>
                {SEVERITY_LABEL[value]}
              </option>
            ))}
          </NativeSelect>
        </Field>
      </div>

      <div className="mt-4 flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={close} disabled={saving}>
          취소
        </Button>
        <Button type="submit" loading={saving} disabled={title.trim().length === 0}>
          추가하기
        </Button>
      </div>
    </form>
  );
}
