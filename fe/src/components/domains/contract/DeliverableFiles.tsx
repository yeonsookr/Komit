"use client";

import * as React from "react";
import { Button } from "@/components/buttons";
import { Input, NativeSelect } from "@/components/common";
import { INTERNAL } from "@/lib/api/endpoints";
import { appPath } from "@/lib/basePath";
import type { Deliverable, DeliverableAccessStage } from "@/types/contract";

export interface DeliverableFilesProps {
  deliverables: Deliverable[];
  /**
   * 개발자가 보는 화면인지. 개발자는 올리고 의뢰인은 받습니다.
   * 접힘 줄의 문구도 갈립니다 — 자기가 올린 것을 「개발자가」라고 부르지 않습니다.
   */
  received: boolean;
  /**
   * 대금 지급이 끝났는지. 개발 계약이 `COMPLETED` 일 때입니다. 의뢰인은 이때부터
   * 지급 후 해금(PAID) 파일을 받습니다 (R-QA-06).
   */
  paid?: boolean;
  /** 파일을 골랐을 때. 개발자 화면에서만 씁니다. */
  onPick?: (deliverable: Deliverable, file: File, accessStage: DeliverableAccessStage) => void;
  /** 인수 시험 URL 을 적었을 때. 개발자 화면에서만 씁니다. */
  onAddUrl?: (deliverable: Deliverable, url: string) => void;
  /** 산출물 항목을 더할 때. 개발자 화면에서만 씁니다. */
  onAddDeliverable?: (title: string) => void;
  /** 올리는 중이면 입력을 잠급니다. */
  busy?: boolean;
}

/** 아무 자료도 없는 산출물인지. 예전 단일 파일 칸(`fileId`)도 자료로 셉니다. */
export function hasNoMaterial(deliverable: Deliverable): boolean {
  return deliverable.materials.length === 0 && deliverable.fileId === null;
}

const LINK_CLASS =
  "rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";
const LOCKED_CLASS = "text-[13px] text-fg-subtle";

/**
 * 산출물 하나에 등록한 자료. URL 과 파일을 모두 보여 주고, URL 이 있다고 파일을 숨기지 않습니다.
 *
 * 지급 전 PAID 파일은 의뢰인에게 링크 대신 받을 수 있는 때를 알립니다. 서버도 409 로 막지만,
 * 링크를 누르면 오류 본문만 담긴 창이 열리므로 여기서 먼저 가립니다 (R-QA-06).
 */
export function DeliverableResources({
  deliverable,
  received = false,
  paid = false,
}: Readonly<{ deliverable: Deliverable; received?: boolean; paid?: boolean }>) {
  // 올린 개발자는 언제든, 의뢰인은 지급이 끝난 뒤에 해금 파일을 받습니다.
  const paidOpen = received || paid;

  return (
    <>
      {deliverable.materials.map((item) => {
        if (item.url !== null) {
          return (
            <a
              key={item.deliverableMaterialId}
              href={item.url}
              target="_blank"
              rel="noopener noreferrer"
              className="break-all text-[13px] text-fg-secondary hover:underline"
            >
              인수 시험 URL: {item.url}
            </a>
          );
        }
        if (item.accessStage === "PAID" && !paidOpen) {
          return (
            <span key={item.deliverableMaterialId} className={LOCKED_CLASS}>
              해금 파일은 지급 완료 후 받을 수 있습니다
            </span>
          );
        }
        return (
          <a
            key={item.deliverableMaterialId}
            href={appPath(INTERNAL.DELIVERABLE_MATERIAL_FILE(item.deliverableMaterialId))}
            className={LINK_CLASS}
          >
            {item.accessStage === "PAID" ? "해금 파일 받기" : "인수 시험 파일 받기"}
          </a>
        );
      })}
      {deliverable.materials.length === 0 && deliverable.fileId !== null && (
        paidOpen ? (
          <a href={appPath(INTERNAL.DELIVERABLE_FILE(deliverable.deliverableId))} className={LINK_CLASS}>
            기존 파일 받기
          </a>
        ) : (
          <span className={LOCKED_CLASS}>기존 파일은 지급 완료 후 받을 수 있습니다</span>
        )
      )}
    </>
  );
}

/** 적은 값을 넘기고 칸을 비우는 한 줄 입력. 비어 있으면 넘기지 않습니다. */
function AddRow({
  label,
  placeholder,
  action,
  disabled,
  onSubmit,
}: Readonly<{
  label: string;
  placeholder: string;
  action: string;
  disabled?: boolean;
  onSubmit: (value: string) => void;
}>) {
  const [value, setValue] = React.useState("");
  const submit = () => {
    const trimmed = value.trim();
    if (trimmed === "") return;
    onSubmit(trimmed);
    setValue("");
  };

  return (
    <div className="flex min-w-0 flex-1 items-center gap-2">
      <Input
        aria-label={label}
        size="sm"
        placeholder={placeholder}
        value={value}
        disabled={disabled}
        onChange={(event) => setValue(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Enter") submit();
        }}
      />
      <Button size="sm" variant="outline" disabled={disabled || value.trim() === ""} onClick={submit}>
        {action}
      </Button>
    </div>
  );
}

/**
 * DeliverableFiles — 인계 작업 안의 파일 줄.
 *
 * **확인 문장과 짝을 짓지 않습니다.** 문장은 의뢰인이 체크하는 것이고 파일은
 * 주고받는 것이라 서로 셈이 다릅니다. 둘을 잇는 연결은 ERD 에도 없습니다.
 *
 * 기본은 접어 둡니다. 파일이 몇 개든 접힌 높이가 한 줄이라, 확인 문장이 파일
 * 목록에 밀려 내려가지 않습니다. 산출물이 하나도 없으면 개발자가 바로 더할 수 있게 펼쳐 둡니다.
 */
export function DeliverableFiles({
  deliverables,
  received,
  paid = false,
  onPick,
  onAddUrl,
  onAddDeliverable,
  busy,
}: Readonly<DeliverableFilesProps>) {
  const canAddDeliverable = received && onAddDeliverable !== undefined;
  const [open, setOpen] = React.useState(deliverables.length === 0);
  const [stages, setStages] = React.useState<Record<number, DeliverableAccessStage>>({});

  if (deliverables.length === 0 && !canAddDeliverable) return null;

  const label = received ? "첨부한 파일" : "개발자가 첨부한 파일";

  return (
    <div className="mt-3 px-4 sm:px-5">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        className="flex w-full items-center justify-between gap-3 rounded-md bg-surface px-3 py-2.5 text-[13px] font-semibold text-fg-secondary hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <span>
          {label} <span className="font-mono tabular-nums">{deliverables.length}</span>개
        </span>
        <span aria-hidden className="text-fg-subtle">
          {open ? "▴" : "▾"}
        </span>
      </button>

      {open && (
        <ul className="mt-1 divide-y divide-line-subtle">
          {deliverables.map((deliverable) => (
            <li key={deliverable.deliverableId} className="flex flex-col gap-2 px-3 py-2">
              <div className="flex items-center justify-between gap-3">
                <span
                  className={
                    hasNoMaterial(deliverable)
                      ? "min-w-0 truncate text-[13px] text-fg-subtle"
                      : "min-w-0 truncate text-[13px] text-fg"
                  }
                >
                  {deliverable.title}
                </span>

                <div className="flex min-w-0 flex-wrap items-center gap-3">
                  <DeliverableResources deliverable={deliverable} received={received} paid={paid} />
                  {!received && hasNoMaterial(deliverable) && (
                    <span className="text-[13px] text-fg-subtle">아직 올라오지 않았습니다</span>
                  )}
                  {received && (
                    <div className="flex flex-wrap items-center gap-2">
                      <NativeSelect
                        aria-label={`${deliverable.title} 파일 용도`}
                        placeholder="파일 용도 선택"
                        disabled={busy}
                        value={stages[deliverable.deliverableId] ?? ""}
                        onChange={(event) => {
                          const stage = event.target.value;
                          if (stage === "TEST" || stage === "PAID") {
                            setStages((previous) => ({ ...previous, [deliverable.deliverableId]: stage }));
                          }
                        }}
                      >
                        <option value="TEST">인수 시험용</option>
                        <option value="PAID">지급 후 해금</option>
                      </NativeSelect>
                      <label className="cursor-pointer text-[13px] font-semibold text-primary hover:underline">
                        파일 추가{" "}
                        <input
                          type="file"
                          className="sr-only"
                          disabled={busy || !stages[deliverable.deliverableId]}
                          onChange={(event) => {
                            const file = event.target.files?.[0];
                            const stage = stages[deliverable.deliverableId];
                            if (file && stage) onPick?.(deliverable, file, stage);
                            event.target.value = "";
                          }}
                        />
                      </label>
                    </div>
                  )}
                </div>
              </div>

              {received && onAddUrl && (
                <AddRow
                  label={`${deliverable.title} 인수 시험 URL`}
                  placeholder="https:// 로 시작하는 인수 시험 주소"
                  action="URL 추가"
                  disabled={busy}
                  onSubmit={(url) => onAddUrl(deliverable, url)}
                />
              )}
            </li>
          ))}

          {canAddDeliverable && (
            <li className="px-3 py-2">
              <AddRow
                label="산출물 항목 이름"
                placeholder="더할 산출물 이름 (계약 범위·금액은 바뀌지 않습니다)"
                action="산출물 추가"
                disabled={busy}
                onSubmit={onAddDeliverable}
              />
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
