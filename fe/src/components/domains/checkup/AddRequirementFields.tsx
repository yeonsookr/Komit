"use client";

import * as React from "react";
import { Button } from "@/components/buttons";
import { Field, Input, NativeSelect } from "@/components/common";
import { cn } from "@/lib/cn";
import { SEVERITY_LABEL } from "@/lib/diagnosisReport";
import type { CodeItem } from "@/types/developer";
import type { IssueSeverity } from "@/types/diagnosis";

const SEVERITIES: IssueSeverity[] = ["HIGH", "MEDIUM", "LOW"];

export interface AddRequirementFieldsProps {
  /** 분류 선택지. `GET /api/codes/ISSUE_CATEGORY` 로 받아 서버에서 내려줍니다. */
  categories: CodeItem[];
  onAdd: (title: string, categoryCodeId: number, severity: IssueSeverity) => void;
  onCancel: () => void;
  className?: string;
}

/**
 * AddRequirementFields — 요구사항 한 줄을 적는 입력칸.
 *
 * 저장은 호출부가 정합니다. 요구사항 선택 화면은 바로 서버에 만들고,
 * 요청서 수정 화면은 편집 상태에만 더했다가 「저장하기」에서 함께 보냅니다.
 */
export function AddRequirementFields({
  categories,
  onAdd,
  onCancel,
  className,
}: Readonly<AddRequirementFieldsProps>) {
  // Field 는 htmlFor 를 직접 받습니다. 라벨과 입력을 이어 두어야 읽어 줍니다.
  const titleId = React.useId();
  const categoryId = React.useId();
  const severityId = React.useId();

  const [title, setTitle] = React.useState("");
  const [categoryCodeId, setCategoryCodeId] = React.useState<number | null>(null);
  const [severity, setSeverity] = React.useState<IssueSeverity>("MEDIUM");

  // 분류 선택지가 오면 첫 항목을 기본으로 둡니다.
  React.useEffect(() => {
    setCategoryCodeId((prev) => prev ?? categories[0]?.codeId ?? null);
  }, [categories]);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (title.trim().length === 0 || categoryCodeId === null) return;
    onAdd(title.trim(), categoryCodeId, severity);
  };

  return (
    <form onSubmit={submit} className={cn("rounded-xl border border-line px-4 py-4", className)}>
      <Field label="무엇을 맡기고 싶으신가요?" htmlFor={titleId}>
        <Input
          id={titleId}
          autoFocus
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="예) 주문 취소 화면 추가"
          maxLength={200}
        />
      </Field>

      <div className="mt-3 flex flex-wrap items-end gap-3">
        <Field label="분류" htmlFor={categoryId} className="min-w-40 flex-1">
          <NativeSelect
            id={categoryId}
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

        <Field label="위험도" htmlFor={severityId} className="min-w-32 flex-1">
          <NativeSelect
            id={severityId}
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
        <Button type="button" variant="ghost" onClick={onCancel}>
          취소
        </Button>
        <Button type="submit" disabled={title.trim().length === 0}>
          추가하기
        </Button>
      </div>
    </form>
  );
}
