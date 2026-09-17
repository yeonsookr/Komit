"use client";

import * as React from "react";
import { INTERNAL } from "@/lib/api/endpoints";
import type { Deliverable } from "@/types/contract";

export interface DeliverableFilesProps {
  deliverables: Deliverable[];
  /**
   * 개발자가 보는 화면인지. 개발자는 올리고 의뢰인은 받습니다.
   * 접힘 줄의 문구도 갈립니다 — 자기가 올린 것을 「개발자가」라고 부르지 않습니다.
   */
  received: boolean;
  /** 파일을 골랐을 때. 개발자 화면에서만 씁니다. */
  onPick?: (deliverable: Deliverable, file: File) => void;
  /** 올리는 중이면 입력을 잠급니다. */
  busy?: boolean;
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
                  deliverable.fileId === null
                    ? "min-w-0 truncate text-[13px] text-fg-subtle"
                    : "min-w-0 truncate text-[13px] text-fg"
                }
              >
                {deliverable.title}
              </span>

              {received ? (
                <label className="shrink-0 cursor-pointer text-[13px] font-semibold text-primary hover:underline">
                  {deliverable.fileId === null ? "파일 첨부" : "다시 올리기"}
                  <input
                    type="file"
                    className="sr-only"
                    disabled={busy}
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      if (file) onPick?.(deliverable, file);
                      event.target.value = "";
                    }}
                  />
                </label>
              ) : deliverable.fileId === null ? (
                <span className="shrink-0 text-[13px] text-fg-subtle">
                  아직 올라오지 않았습니다
                </span>
              ) : (
                <a
                  href={INTERNAL.DELIVERABLE_FILE(deliverable.deliverableId)}
                  className="shrink-0 rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  받기
                </a>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
