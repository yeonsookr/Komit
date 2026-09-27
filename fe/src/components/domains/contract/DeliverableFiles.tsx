"use client";

import * as React from "react";
import { NativeSelect } from "@/components/common";
import { INTERNAL } from "@/lib/api/endpoints";
import type { Deliverable, DeliverableAccessStage } from "@/types/contract";

export interface DeliverableFilesProps {
  deliverables: Deliverable[];
  /**
   * 개발자가 보는 화면인지. 개발자는 올리고 의뢰인은 받습니다.
   * 접힘 줄의 문구도 갈립니다 — 자기가 올린 것을 「개발자가」라고 부르지 않습니다.
   */
  received: boolean;
  /** 파일을 골랐을 때. 개발자 화면에서만 씁니다. */
  onPick?: (deliverable: Deliverable, file: File, accessStage: DeliverableAccessStage) => void;
  /** 올리는 중이면 입력을 잠급니다. */
  busy?: boolean;
}

/** 아무 자료도 없는 산출물인지. 예전 단일 파일 칸(`fileId`)도 자료로 셉니다. */
export function hasNoMaterial(deliverable: Deliverable): boolean {
  return deliverable.materials.length === 0 && deliverable.fileId === null;
}

/**
 * 산출물 하나에 등록한 자료. URL 과 파일을 모두 보여 주고, URL 이 있다고 파일을 숨기지 않습니다.
 * 지급 전 PAID 파일을 막는 것은 서버입니다. 여기서는 링크 문구만 가릅니다.
 */
export function DeliverableResources({ deliverable }: Readonly<{ deliverable: Deliverable }>) {
  return (
    <>
      {deliverable.materials.map((item) =>
        item.url === null ? (
          <a
            key={item.deliverableMaterialId}
            href={INTERNAL.DELIVERABLE_MATERIAL_FILE(item.deliverableMaterialId)}
            className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            {item.accessStage === "PAID" ? "지급 완료 후 파일 받기" : "인수 시험 파일 받기"}
          </a>
        ) : (
          <span key={item.deliverableMaterialId} className="break-all text-[13px] text-fg-secondary">
            인수 시험 URL: {item.url}
          </span>
        ),
      )}
      {deliverable.materials.length === 0 && deliverable.fileId !== null && (
        <a href={INTERNAL.DELIVERABLE_FILE(deliverable.deliverableId)}>
          지급 완료 후 기존 파일 받기
        </a>
      )}
    </>
  );
}

/**
 * DeliverableFiles — 인계 작업 안의 파일 줄.
 *
 * **확인 문장과 짝을 짓지 않습니다.** 문장은 의뢰인이 체크하는 것이고 파일은
 * 주고받는 것이라 서로 셈이 다릅니다. 둘을 잇는 연결은 ERD 에도 없습니다.
 *
 * 기본은 접어 둡니다. 파일이 몇 개든 접힌 높이가 한 줄이라, 확인 문장이 파일
 * 목록에 밀려 내려가지 않습니다.
 */
export function DeliverableFiles({
  deliverables,
  received,
  onPick,
  busy,
}: Readonly<DeliverableFilesProps>) {
  const [open, setOpen] = React.useState(false);
  const [stages, setStages] = React.useState<Record<number, DeliverableAccessStage>>({});

  if (deliverables.length === 0) return null;

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
            <li
              key={deliverable.deliverableId}
              className="flex items-center justify-between gap-3 px-3 py-2"
            >
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
                <DeliverableResources deliverable={deliverable} />
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
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
