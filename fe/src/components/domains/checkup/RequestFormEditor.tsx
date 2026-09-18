"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button, IconButton } from "@/components/buttons";
import { Card, Input, Mono } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import {
  canSave,
  newCriterion,
  newRequirement,
  toDraft,
  toRequest,
  type DraftRequirement,
} from "@/lib/requirementDraft";
import { ApiError } from "@/types/api";
import type { CodeItem } from "@/types/developer";
import type { Diagnosis, Issue } from "@/types/diagnosis";
import { AddRequirementFields } from "./AddRequirementFields";

export interface RequestFormEditorProps {
  diagnosis: Diagnosis;
  issues: Issue[];
  /** 「+ 요구사항 추가」 의 분류 선택지. */
  categories: CodeItem[];
}

/**
 * RequestFormEditor — 요청서의 요구사항과 확인 문장을 고칩니다.
 *
 * **저장 버튼 하나가 요청 하나입니다.** 줄마다 API 를 부르지 않고 화면이 전체
 * 상태를 들고 있다가 통째로 보냅니다. 지운 줄은 보낸 목록에서 빠지는 것으로
 * 서버가 알아차립니다(목 명세).
 */
export function RequestFormEditor({
  diagnosis,
  issues,
  categories,
}: Readonly<RequestFormEditorProps>) {
  const router = useRouter();
  const showToast = useToast();

  const [draft, setDraft] = React.useState<DraftRequirement[]>(() => toDraft(issues));
  const [adding, setAdding] = React.useState(false);
  const [saving, setSaving] = React.useState(false);

  /** 한 요구사항만 바꿉니다. */
  const patch = (key: string, change: (row: DraftRequirement) => DraftRequirement) =>
    setDraft((prev) => prev.map((row) => (row.key === key ? change(row) : row)));

  const save = async () => {
    if (saving || !canSave(draft)) return;
    setSaving(true);

    try {
      await clientApi.put(INTERNAL.REQUIREMENTS(diagnosis.diagnosisId), {
        requirements: toRequest(draft),
      });

      showToast({ tone: "success", title: "요청서를 저장했습니다." });
      router.push(ROUTES.checkupRequest(diagnosis.diagnosisId));
    } catch (error) {
      const apiError = error instanceof ApiError ? error : null;

      // 이미 계약에 들어간 요구사항은 뺄 수 없습니다.
      showToast({
        tone: "danger",
        title: apiError?.message ?? "요청서를 저장하지 못했습니다.",
        description:
          apiError?.code === "ISSUE_STATUS_CONFLICT"
            ? "계약에 들어간 요구사항은 요청서에서 뺄 수 없습니다."
            : undefined,
      });
      setSaving(false);
    }
  };

  return (
    <>
      <header>
        <h1 className="text-[28px] leading-tight font-bold text-fg">요청서 수정</h1>
        <p className="mt-1.5 text-[13px] text-fg-subtle">
          {diagnosis.project.name} · 요청서의 요구사항을 수정합니다
        </p>
      </header>

      <div className="mt-8 flex items-baseline justify-between gap-4">
        <h2 className="text-base font-bold text-fg">요구사항</h2>
        <p className="text-[13px] text-fg-subtle">
          <span className="font-mono tabular-nums">{draft.length}</span>건
        </p>
      </div>

      <Card variant="outline" padding="none" className="mt-3">
        {draft.length === 0 ? (
          <p className="px-5 py-10 text-center text-sm text-fg-muted">
            요구사항을 모두 뺐습니다. 한 건 이상 있어야 저장할 수 있습니다.
          </p>
        ) : (
          <ul>
            {draft.map((requirement) => (
              <li
                key={requirement.key}
                className="px-5 py-5 [&:not(:first-child)]:border-t [&:not(:first-child)]:border-line-subtle"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-bold text-fg">{requirement.title}</p>
                    {requirement.location && (
                      <Mono className="mt-1 block text-[13px] text-fg-subtle">
                        {requirement.location}
                      </Mono>
                    )}
                  </div>

                  <Button
                    variant="danger"
                    size="sm"
                    className="shrink-0"
                    onClick={() =>
                      setDraft((prev) => prev.filter((row) => row.key !== requirement.key))
                    }
                  >
                    요구사항 삭제
                  </Button>
                </div>

                <ul className="mt-3 flex flex-col gap-2">
                  {requirement.criteria.map((criterion) => (
                    <li key={criterion.key} className="flex items-center gap-2">
                      <Input
                        className="min-w-0 flex-1"
                        value={criterion.testCase}
                        placeholder="예) 관리자 주소로 들어가면 로그인 화면이 뜬다"
                        aria-label="확인 문장"
                        maxLength={500}
                        onChange={(event) =>
                          patch(requirement.key, (row) => ({
                            ...row,
                            criteria: row.criteria.map((item) =>
                              item.key === criterion.key
                                ? { ...item, testCase: event.target.value }
                                : item,
                            ),
                          }))
                        }
                      />
                      <IconButton
                        variant="ghost"
                        aria-label="확인 문장 삭제"
                        onClick={() =>
                          patch(requirement.key, (row) => ({
                            ...row,
                            criteria: row.criteria.filter((item) => item.key !== criterion.key),
                          }))
                        }
                      >
                        ✕
                      </IconButton>
                    </li>
                  ))}
                </ul>

                <button
                  type="button"
                  onClick={() =>
                    patch(requirement.key, (row) => ({
                      ...row,
                      criteria: [...row.criteria, newCriterion()],
                    }))
                  }
                  className="mt-2 w-full rounded-md border border-line py-2.5 text-[13px] font-semibold text-primary hover:bg-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  + 확인 문장 추가
                </button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {adding ? (
        <AddRequirementFields
          categories={categories}
          className="mt-4"
          onCancel={() => setAdding(false)}
          onAdd={(title, categoryCodeId, severity) => {
            setDraft((prev) => [...prev, newRequirement(title, categoryCodeId, severity)]);
            setAdding(false);
          }}
        />
      ) : (
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="mt-4 w-full rounded-md border border-primary-border bg-primary-soft py-3.5 text-sm font-semibold text-primary hover:bg-primary-soft-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          + 요구사항 추가
        </button>
      )}

      {/* 목 명세: 금액이나 기간이 달라질 내용이면 개발자가 계약서를 다시 보낸다. */}
      <p className="mt-6 text-[13px] text-fg-muted">
        금액이나 기간이 달라지면 개발자가 계약서를 다시 보냅니다.
      </p>

      <Button
        variant="dark"
        size="lg"
        fullWidth
        className="mt-3"
        onClick={save}
        loading={saving}
        disabled={!canSave(draft)}
      >
        저장하기
      </Button>
    </>
  );
}
