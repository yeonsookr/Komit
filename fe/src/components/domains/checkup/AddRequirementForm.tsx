"use client";

import * as React from "react";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ApiError } from "@/types/api";
import type { CodeItem } from "@/types/developer";
import type { Issue, IssueSeverity } from "@/types/diagnosis";
import { AddRequirementFields } from "./AddRequirementFields";

export interface AddRequirementFormProps {
  diagnosisId: number;
  /** 분류 선택지. `GET /api/codes/ISSUE_CATEGORY` 로 받아 서버에서 내려줍니다. */
  categories: CodeItem[];
  onAdded: (issue: Issue) => void;
}

/**
 * AddRequirementForm — 검사에서 찾지 못한 요구사항을 직접 적어 **바로 만듭니다.**
 *
 * 평소에는 「+ 요구사항 추가」 한 줄이고, 누르면 입력칸이 열립니다.
 * 입력칸 자체는 `AddRequirementFields` 가 그리고, 여기서는 서버에 만드는 일만 합니다.
 * (요청서 수정 화면은 서버에 바로 만들지 않고 「저장하기」에서 함께 보냅니다.)
 */
export function AddRequirementForm({
  diagnosisId,
  categories,
  onAdded,
}: Readonly<AddRequirementFormProps>) {
  const showToast = useToast();

  const [open, setOpen] = React.useState(false);
  const [saving, setSaving] = React.useState(false);

  const add = async (title: string, categoryCodeId: number, severity: IssueSeverity) => {
    if (saving) return;
    setSaving(true);

    try {
      const { data } = await clientApi.post<Issue>(INTERNAL.ISSUES(diagnosisId), {
        title,
        categoryCodeId,
        severity,
      });

      onAdded(data);
      showToast({ tone: "success", title: "요구사항을 추가했습니다." });
      setOpen(false);
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
    <AddRequirementFields
      className="mt-4"
      categories={categories}
      onAdd={add}
      onCancel={() => setOpen(false)}
    />
  );
}
