"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button, IconButton } from "@/components/buttons";
import {
  Card,
  CardHeader,
  Field,
  Input,
  Money,
  Textarea,
} from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { isDevelopmentDraft } from "@/lib/contract";
import {
  applyQaSuggestion,
  canSendMilestones,
  emptyMilestone,
  filledLines,
  HANDOVER_TITLE,
  isUnpriced,
  toEditorState,
  toWriteMilestones,
  type EditorHandover,
  type EditorMilestone,
} from "@/lib/contractEditor";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract, QaSuggestionList } from "@/types/contract";
import type { Issue } from "@/types/diagnosis";

/** 요청서가 없을 때 쓰는 빈 목록. 그릴 때마다 새 배열을 만들지 않습니다. */
const NO_ISSUES: Issue[] = [];

export interface ContractEditorProps {
  roomId: number;
  /** 고칠 계약서. 개발자가 보낸 것이거나 AI version 1 입니다. 둘 다 없으면 null. */
  contract: Contract | null;
  /** 계약서가 없을 때 작업으로 채울 요청서의 요구사항. AI 초안을 받지 못했을 때 씁니다. */
  requested?: Issue[];
  counterpartName: string;
}

/**
 * ContractEditor — 개발 계약서 작성과 수정.
 *
 * **둘이 같은 요청입니다.** 협의 중에 다시 보내면 덮어쓰므로 작성과 수정이
 * 갈리지 않습니다. 서명한 뒤에는 고칠 수 없습니다 (R-CON-02).
 *
 * 작업 가격의 합이 계약 금액이 됩니다. 인계 작업은 0 원이라 합계에서 빠지고
 * 맨 아래 한 블록으로만 나옵니다 (R-CON-04 · R-QA-04).
 *
 * AI version 1 은 요청서와 상담 대화로 만든 초안입니다. 작업 이름·설명·확인 문장을 채워 두고
 * 금액·기간·일정은 비워 둡니다. 초안에서 온 작업도 모두 고칠 수 있습니다.
 */
export function ContractEditor({
  roomId,
  contract,
  requested = NO_ISSUES,
  counterpartName,
}: Readonly<ContractEditorProps>) {
  const router = useRouter();
  const showToast = useToast();

  /** 개발자가 이미 보낸 계약서인지. AI 초안은 아직 보내지 않은 것이라 새로 쓰는 것과 같습니다. */
  const sent = contract !== null && !isDevelopmentDraft(contract);
  const development = sent ? (contract?.development ?? null) : null;
  const initial = React.useMemo(
    () => toEditorState(contract, requested),
    [contract, requested],
  );
  const [drafts, setDrafts] = React.useState<EditorMilestone[]>(
    initial.milestones,
  );
  const [handover, setHandover] = React.useState<EditorHandover>(
    initial.handover,
  );
  const [startDate, setStartDate] = React.useState(
    development?.startDate ?? "",
  );
  const [dueDate, setDueDate] = React.useState(development?.dueDate ?? "");
  const [freeRepairDays, setFreeRepairDays] = React.useState(
    String(development?.freeRepairDays ?? 14),
  );
  const [deliverables, setDeliverables] = React.useState<string[]>(
    development?.deliverables.map((item) => item.title) ?? [],
  );
  const [message, setMessage] = React.useState("");
  const [saving, setSaving] = React.useState(false);
  const [suggestingKey, setSuggestingKey] = React.useState<string | null>(null);
  const [suggestions, setSuggestions] = React.useState<Record<string, string[]>>({});

  const total = drafts.reduce(
    (sum, draft) => sum + (Number(draft.price) || 0),
    0,
  );
  const days = drafts.reduce(
    (sum, draft) => sum + (Number(draft.workDate) || 0),
    0,
  );
  /** 인계 작업까지 더한 수. 모두 통과해야 대금이 나갑니다. */
  const allCount = drafts.length + 1;

  const patch = (key: string, next: Partial<EditorMilestone>) =>
    setDrafts((prev) =>
      prev.map((d) => (d.key === key ? { ...d, ...next } : d)),
    );

  const patchQa = (key: string, index: number, value: string) =>
    setDrafts((prev) =>
      prev.map((d) =>
        d.key === key
          ? {
              ...d,
              qaCriteria: d.qaCriteria.map((qa, i) =>
                i === index ? value : qa,
              ),
            }
          : d,
      ),
    );

  const comma = (digits: string) =>
    digits === "" ? "" : Number(digits).toLocaleString("ko-KR");

  const unpriced = drafts.some(isUnpriced);
  const canSend =
    startDate !== "" && dueDate !== "" && canSendMilestones(drafts, handover);

  const suggestQa = async (draft: EditorMilestone) => {
    if (draft.title.trim() === "" || suggestingKey !== null) return;
    setSuggestingKey(draft.key);
    try {
      const { data } = await clientApi.post<QaSuggestionList>(
        INTERNAL.CONTRACT_QA_SUGGESTIONS(roomId),
        {
          issueId: draft.issueId,
          title: draft.title.trim(),
          detail: draft.detail.trim() === "" ? null : draft.detail.trim(),
        },
        // Next 서버가 백엔드 AI 응답을 30초까지 기다리므로 브라우저는 조금 더 기다립니다.
        { timeout: 35_000 },
      );
      setSuggestions((prev) => ({
        ...prev,
        [draft.key]: data.content.map((item) => item.testCase),
      }));
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "확인 문장을 추천하지 못했습니다.",
      });
    } finally {
      setSuggestingKey(null);
    }
  };

  const send = async () => {
    if (!canSend || saving) return;
    setSaving(true);

    try {
      await clientApi.post(INTERNAL.DEVELOPMENT_CONTRACTS(roomId), {
        startDate,
        dueDate,
        freeRepairDays: Number(freeRepairDays) || 0,
        milestones: toWriteMilestones(drafts, handover),
        deliverableTitles: filledLines(deliverables),
        message: message.trim() === "" ? null : message.trim(),
      });
      showToast({
        tone: "success",
        title: sent ? "계약서를 다시 보냈습니다." : "계약서를 보냈습니다.",
        description: `${counterpartName}님이 확인하고 서명합니다.`,
      });
      router.push(ROUTES.roomContract(roomId));
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "보내지 못했습니다.",
      });
      setSaving(false);
    }
  };

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">
          {sent ? "계약서 수정" : "개발 계약서 작성"}
        </h2>
        <p className="mt-1.5 text-[13px] leading-6 text-fg-subtle">
          {sent
            ? `고치면 ${counterpartName}님에게 다시 보냅니다.`
            : `${counterpartName}님이 개발 계약으로 진행하기로 했습니다. 요구사항마다 금액과 기간을 정하고 계약 조건을 붙여 보냅니다.`}
        </p>
      </header>

      <div className="mt-6">
        <CardHeader
          title="작업과 확인 문장"
          action={
            <span className="text-[13px] text-fg-subtle">
              <span className="font-mono tabular-nums">{drafts.length}</span>건
              · 합계 <Money value={total} className="text-fg-secondary" />
              {days > 0 && (
                <>
                  {" · "}
                  <span className="font-mono tabular-nums">{days}</span>일
                </>
              )}
            </span>
          }
        />

        <Card className="mt-3" padding="none">
          <ul className="divide-y divide-line-subtle">
            {drafts.map((draft) => (
              <li key={draft.key} className="px-4 py-4 sm:px-5">
                {/* 넓으면 한 줄, 좁으면 이름과 금액·기간을 나눕니다. */}
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                  <Input
                    className="min-w-0 flex-1"
                    value={draft.title}
                    placeholder="작업 이름"
                    onChange={(event) =>
                      patch(draft.key, { title: event.target.value })
                    }
                  />
                  <div className="flex items-center gap-2">
                    <Input
                      mono
                      suffix="원"
                      containerClassName="w-32"
                      inputMode="numeric"
                      value={comma(draft.price)}
                      onChange={(event) =>
                        patch(draft.key, {
                          price: event.target.value.replace(/\D/g, ""),
                        })
                      }
                    />
                    <Input
                      mono
                      suffix="일"
                      containerClassName="w-24"
                      inputMode="numeric"
                      value={draft.workDate}
                      onChange={(event) =>
                        patch(draft.key, {
                          workDate: event.target.value.replace(/\D/g, ""),
                        })
                      }
                    />
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={() =>
                        setDrafts((prev) =>
                          prev.filter((d) => d.key !== draft.key),
                        )
                      }
                    >
                      작업 삭제
                    </Button>
                  </div>
                </div>

                <Textarea
                  className="mt-2 min-h-[64px]"
                  rows={2}
                  maxLength={1000}
                  value={draft.detail}
                  placeholder="작업 설명 (선택)"
                  onChange={(event) =>
                    patch(draft.key, { detail: event.target.value })
                  }
                />

                <ul className="mt-3 flex flex-col gap-2">
                  {draft.qaCriteria.map((qa, index) => (
                    <li key={index} className="flex items-center gap-2">
                      <Input
                        className="min-w-0 flex-1"
                        value={qa}
                        placeholder="확인 문장"
                        onChange={(event) =>
                          patchQa(draft.key, index, event.target.value)
                        }
                      />
                      <IconButton
                        aria-label="확인 문장 삭제"
                        onClick={() =>
                          patch(draft.key, {
                            qaCriteria: draft.qaCriteria.filter(
                              (_, i) => i !== index,
                            ),
                          })
                        }
                      >
                        ✕
                      </IconButton>
                    </li>
                  ))}
                </ul>

                <Button
                  variant="ghost"
                  size="sm"
                  className="mt-2"
                  onClick={() =>
                    patch(draft.key, { qaCriteria: [...draft.qaCriteria, ""] })
                  }
                >
                  + 확인 문장 추가
                </Button>

                <Button
                  variant="outline"
                  size="sm"
                  className="mt-2 ml-2"
                  loading={suggestingKey === draft.key}
                  disabled={draft.title.trim() === "" || suggestingKey !== null}
                  onClick={() => void suggestQa(draft)}
                >
                  AI 확인 문장 추천
                </Button>

                {(suggestions[draft.key]?.length ?? 0) > 0 && (
                  <div className="mt-3 rounded-lg bg-surface px-3 py-3" aria-live="polite">
                    <p className="text-xs font-semibold text-fg-secondary">추천 후보</p>
                    <ul className="mt-2 flex flex-col gap-2">
                      {(suggestions[draft.key] ?? []).map((candidate) => (
                        <li key={candidate}>
                          <button
                            type="button"
                            className="w-full rounded-md border border-line bg-bg px-3 py-2 text-left text-sm leading-6 text-fg-secondary transition-colors hover:border-primary hover:text-fg"
                            onClick={() =>
                              patch(draft.key, {
                                qaCriteria: applyQaSuggestion(draft.qaCriteria, candidate),
                              })
                            }
                          >
                            {candidate}
                          </button>
                        </li>
                      ))}
                    </ul>
                    <p className="mt-2 text-xs text-fg-subtle">
                      후보를 누르면 확인 문장에 추가됩니다. 추가한 뒤에도 문구를 고칠 수 있습니다.
                    </p>
                  </div>
                )}
              </li>
            ))}

            {/* 인계 작업. 가격도 기간도 없고 넘겨받았는지 확인하는 문장만 있습니다. */}
            <li className="px-4 py-4 sm:px-5">
              <p className="text-sm font-semibold text-fg">{HANDOVER_TITLE}</p>

              <ul className="mt-3 flex flex-col gap-2">
                {handover.lines.map((line, index) => (
                  <li key={index} className="flex items-center gap-2">
                    <Input
                      className="min-w-0 flex-1"
                      value={line}
                      placeholder="확인 문장"
                      onChange={(event) =>
                        setHandover((prev) => ({
                          ...prev,
                          lines: prev.lines.map((l, i) =>
                            i === index ? event.target.value : l,
                          ),
                        }))
                      }
                    />
                    <IconButton
                      aria-label="인계 항목 삭제"
                      onClick={() =>
                        setHandover((prev) => ({
                          ...prev,
                          lines: prev.lines.filter((_, i) => i !== index),
                        }))
                      }
                    >
                      ✕
                    </IconButton>
                  </li>
                ))}
              </ul>

              <Button
                variant="ghost"
                size="sm"
                className="mt-2"
                onClick={() =>
                  setHandover((prev) => ({ ...prev, lines: [...prev.lines, ""] }))
                }
              >
                + 인계 항목 추가
              </Button>
            </li>
          </ul>
        </Card>

        <Button
          variant="outline"
          fullWidth
          className="mt-3"
          onClick={() => setDrafts((prev) => [...prev, emptyMilestone()])}
        >
          + 작업 추가
        </Button>

        <p className="mt-3 text-[13px] text-fg-subtle">
          작업 <span className="font-mono tabular-nums">{allCount}</span>건을
          모두 통과하면 대금이 한 번에 나갑니다.
        </p>
        {unpriced && (
          <p className="mt-1 text-[13px] text-fg-subtle">
            금액을 정하지 않은 작업이 있습니다. 0원은 인계 작업에만 씁니다.
          </p>
        )}
      </div>

      <div className="mt-10">
        <CardHeader title="계약 조건" />
        <Card className="mt-3">
          <div className="flex flex-col gap-5">
            {/* 두 날짜는 한 줄에 둡니다. 떨어뜨려 놓으면 사이 기간이 안 보입니다. */}
            <div className="flex flex-col gap-5 sm:flex-row sm:flex-wrap sm:items-start sm:gap-x-6">
              <Field layout="horizontal" label="착수일">
                <Input
                  type="date"
                  className="w-40"
                  value={startDate}
                  onChange={(event) => setStartDate(event.target.value)}
                />
              </Field>
              {/* 작업 기간의 합. 납기일이 어떻게 나왔는지 알려 주는 참고값입니다. */}
              <Field
                layout="horizontal"
                label="납기일"
                helper={days > 0 ? `작업 기간 합계 ${days}일` : undefined}
              >
                <Input
                  type="date"
                  className="w-40"
                  value={dueDate}
                  onChange={(event) => setDueDate(event.target.value)}
                />
              </Field>
            </div>
            <Field layout="horizontal" label="하자보수">
              <Input
                mono
                suffix="일"
                containerClassName="w-32"
                inputMode="numeric"
                value={freeRepairDays}
                onChange={(event) =>
                  setFreeRepairDays(event.target.value.replace(/\D/g, ""))
                }
              />
            </Field>
            <Field
              layout="horizontal"
              label="인계물"
              helper="넘겨받을 것을 한 줄에 하나씩 적습니다."
            >
              <div className="flex flex-col gap-2">
                {deliverables.map((title, index) => (
                  <div key={index} className="flex items-center gap-2">
                    <Input
                      className="min-w-0 flex-1"
                      value={title}
                      onChange={(event) =>
                        setDeliverables((prev) =>
                          prev.map((t, i) =>
                            i === index ? event.target.value : t,
                          ),
                        )
                      }
                    />
                    <IconButton
                      aria-label="인계물 삭제"
                      onClick={() =>
                        setDeliverables((prev) =>
                          prev.filter((_, i) => i !== index),
                        )
                      }
                    >
                      ✕
                    </IconButton>
                  </div>
                ))}
                <Button
                  variant="ghost"
                  size="sm"
                  className="self-start"
                  onClick={() => setDeliverables((prev) => [...prev, ""])}
                >
                  + 항목 추가
                </Button>
              </div>
            </Field>
          </div>
        </Card>
      </div>

      <div className="mt-10">
        <CardHeader title="전달 메시지" />
        <Textarea
          className="mt-3"
          rows={4}
          tone="surface"
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          placeholder="금액을 어떻게 잡았는지 같이 적어두면 좋습니다."
        />
      </div>

      <div className="mt-10 flex justify-end">
        <Button
          variant="dark"
          size="lg"
          loading={saving}
          disabled={!canSend}
          onClick={send}
        >
          {sent ? "계약서 다시 보내기" : "계약서 보내기"}
        </Button>
      </div>
    </section>
  );
}
