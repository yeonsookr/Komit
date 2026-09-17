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
import { isHandover } from "@/lib/contract";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { Contract, WriteMilestoneRequest } from "@/types/contract";

/** 편집 중인 작업 한 줄. 화면이 들고 있는 값이라 식별자가 없어도 됩니다. */
interface DraftMilestone {
  key: string;
  title: string;
  price: string;
  workDate: string;
  qaCriteria: string[];
}

/** 인계 작업의 이름. 가격이 0 원인 작업 하나로 계약서에 들어갑니다 (R-CON-04). */
const HANDOVER_TITLE = "인계 · 소스 · 계정 · 설명서";

let counter = 0;
const nextKey = () => `draft-${(counter += 1)}`;

const emptyDraft = (): DraftMilestone => ({
  key: nextKey(),
  title: "",
  price: "",
  workDate: "",
  qaCriteria: [""],
});

/**
 * 이미 있는 계약서를 편집 상태로 풉니다.
 * 인계 작업은 따로 떼어 냅니다 — 가격도 기간도 없는 마지막 블록입니다.
 */
function toDrafts(contract: Contract | null): {
  billable: DraftMilestone[];
  handover: string[];
} {
  const milestones = contract?.development?.milestones ?? [];
  const billable = milestones
    .filter((milestone) => !isHandover(milestone))
    .map((milestone) => ({
      key: nextKey(),
      title: milestone.title,
      price: String(milestone.price),
      workDate: milestone.workDate === null ? "" : String(milestone.workDate),
      qaCriteria: milestone.qaCriteria.map((qa) => qa.testCase ?? ""),
    }));

  const handoverMilestone = milestones.find(isHandover);

  return {
    billable: billable.length === 0 ? [emptyDraft()] : billable,
    handover: handoverMilestone?.qaCriteria.map((qa) => qa.testCase ?? "") ?? [
      "",
    ],
  };
}

export interface ContractEditorProps {
  roomId: number;
  /** 고칠 계약서. 새로 쓰는 것이면 null. */
  contract: Contract | null;
  /** 이 계약에서 다룰 요구사항 식별자. 요청서에서 옵니다. */
  issueIds: number[];
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
 */
export function ContractEditor({
  roomId,
  contract,
  issueIds,
  counterpartName,
}: Readonly<ContractEditorProps>) {
  const router = useRouter();
  const showToast = useToast();

  const development = contract?.development ?? null;
  const initial = React.useMemo(() => toDrafts(contract), [contract]);
  const [drafts, setDrafts] = React.useState<DraftMilestone[]>(
    initial.billable,
  );
  const [handover, setHandover] = React.useState<string[]>(initial.handover);
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

  const patch = (key: string, next: Partial<DraftMilestone>) =>
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

  const filled = (lines: string[]) =>
    lines.map((line) => line.trim()).filter((l) => l !== "");

  const canSend =
    startDate !== "" &&
    dueDate !== "" &&
    drafts.length > 0 &&
    filled(handover).length > 0 &&
    drafts.every(
      (d) =>
        d.title.trim() !== "" &&
        filled(d.qaCriteria).length > 0 &&
        Number.isInteger(Number(d.price)),
    );

  const send = async () => {
    if (!canSend || saving) return;
    setSaving(true);

    const milestones: WriteMilestoneRequest[] = drafts.map((d) => ({
      title: d.title.trim(),
      price: Number(d.price) || 0,
      ...(d.workDate.trim() === "" ? {} : { workDate: Number(d.workDate) }),
      qaCriteria: filled(d.qaCriteria),
    }));

    // 인계는 늘 마지막 작업입니다. 가격이 0 이라 합계에는 들어가지 않습니다.
    milestones.push({
      title: HANDOVER_TITLE,
      price: 0,
      qaCriteria: filled(handover),
    });

    try {
      await clientApi.post(INTERNAL.DEVELOPMENT_CONTRACTS(roomId), {
        issueIds,
        startDate,
        dueDate,
        freeRepairDays: Number(freeRepairDays) || 0,
        milestones,
        deliverableTitles: filled(deliverables),
        message: message.trim() === "" ? null : message.trim(),
      });
      showToast({
        tone: "success",
        title:
          contract === null
            ? "계약서를 보냈습니다."
            : "계약서를 다시 보냈습니다.",
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
          {contract === null ? "개발 계약서 작성" : "계약서 수정"}
        </h2>
        <p className="mt-1.5 text-[13px] leading-6 text-fg-subtle">
          {contract === null
            ? `${counterpartName}님이 개발 계약으로 진행하기로 했습니다. 요구사항마다 금액과 기간을 정하고 계약 조건을 붙여 보냅니다.`
            : `고치면 ${counterpartName}님에게 다시 보냅니다.`}
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
              </li>
            ))}

            {/* 인계 작업. 가격도 기간도 없고 넘겨받았는지 확인하는 문장만 있습니다. */}
            <li className="px-4 py-4 sm:px-5">
              <p className="text-sm font-semibold text-fg">{HANDOVER_TITLE}</p>

              <ul className="mt-3 flex flex-col gap-2">
                {handover.map((line, index) => (
                  <li key={index} className="flex items-center gap-2">
                    <Input
                      className="min-w-0 flex-1"
                      value={line}
                      placeholder="확인 문장"
                      onChange={(event) =>
                        setHandover((prev) =>
                          prev.map((l, i) =>
                            i === index ? event.target.value : l,
                          ),
                        )
                      }
                    />
                    <IconButton
                      aria-label="인계 항목 삭제"
                      onClick={() =>
                        setHandover((prev) =>
                          prev.filter((_, i) => i !== index),
                        )
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
                onClick={() => setHandover((prev) => [...prev, ""])}
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
          onClick={() => setDrafts((prev) => [...prev, emptyDraft()])}
        >
          + 작업 추가
        </Button>

        <p className="mt-3 text-[13px] text-fg-subtle">
          작업 <span className="font-mono tabular-nums">{allCount}</span>건을
          모두 통과하면 대금이 한 번에 나갑니다.
        </p>
      </div>

      <div className="mt-10">
        <CardHeader title="계약 조건" />
        <Card className="mt-3">
          <div className="flex flex-col gap-5">
            <Field label="착수일">
              <Input
                type="date"
                value={startDate}
                onChange={(event) => setStartDate(event.target.value)}
              />
            </Field>
            <Field
              label="끝나는 날"
              helper="작업마다 따로 납기를 두지 않습니다."
            >
              <Input
                type="date"
                value={dueDate}
                onChange={(event) => setDueDate(event.target.value)}
              />
            </Field>
            <Field label="하자보수">
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
          {contract === null ? "계약서 보내기" : "계약서 다시 보내기"}
        </Button>
      </div>
    </section>
  );
}
