"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Avatar, Checkbox, Divider, Tag } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";
import type { RecommendationBatch, RecommendationItem } from "@/types/recommendation";
import type { RoomSummary } from "@/types/room";

export interface RecommendationPickerProps {
  batch: RecommendationBatch;
}

/**
 * RecommendationPicker — 추천 개발자를 골라 상담을 요청합니다.
 *
 * 여러 명에게 같은 요청서를 한 번에 보낼 수 있고, 개발자마다 무료 상담방이
 * 하나씩 열립니다. 요청은 멱등해서 이미 방이 있으면 그 방이 그대로 옵니다.
 */
export function RecommendationPicker({ batch }: Readonly<RecommendationPickerProps>) {
  const router = useRouter();
  const showToast = useToast();

  const [selected, setSelected] = React.useState<number[]>([]);
  const [sending, setSending] = React.useState(false);

  const toggle = (itemId: number) =>
    setSelected((prev) =>
      prev.includes(itemId) ? prev.filter((id) => id !== itemId) : [...prev, itemId],
    );

  const send = async () => {
    if (selected.length === 0 || sending) return;
    setSending(true);

    try {
      const { data } = await clientApi.post<{ content: RoomSummary[] }>(
        INTERNAL.CONSULTATION_REQUESTS(batch.batchId),
        { recommendationItemIds: selected },
      );

      // 방은 보낸 순서가 아니라 recommendationItemId 로 맞춥니다.
      const first = data.content.find(
        (room) => room.recommendationItemId === selected[0],
      );

      showToast({
        tone: "success",
        title: `${selected.length}명에게 상담을 요청했습니다.`,
      });

      // 한 명이면 그 방으로, 여럿이면 목록에서 고르도록 첫 방으로 보냅니다.
      if (first) router.push(ROUTES.room(first.roomId));
      else router.refresh();
    } catch (error) {
      const message =
        error instanceof ApiError ? error.message : "상담을 요청하지 못했습니다.";
      showToast({ tone: "danger", title: message });
      setSending(false);
    }
  };

  if (batch.items.length === 0) {
    return (
      <div className="mt-6">
        <p className="rounded-xl bg-surface px-6 py-10 text-center text-sm text-fg-muted">
          이번 회차에는 맞는 개발자를 찾지 못했습니다. 직접 찾아보실 수 있습니다.
        </p>
        <div className="mt-6 text-center">
          <Link
            href={ROUTES.developers}
            className="rounded-sm text-sm text-fg-muted hover:text-fg hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            다른 개발자 찾기
          </Link>
        </div>
      </div>
    );
  }

  return (
    <>
      <ul className="mt-4">
        {batch.items.map((item, index) => (
          <li key={item.recommendationItemId}>
            {index > 0 && <Divider />}
            <RecommendationRow
              item={item}
              checked={selected.includes(item.recommendationItemId)}
              onToggle={() => toggle(item.recommendationItemId)}
            />
          </li>
        ))}
      </ul>

      <div className="mt-8 text-center">
        <Link
          href={ROUTES.developers}
          className="rounded-sm text-sm text-fg-muted hover:text-fg hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          다른 개발자 찾기
        </Link>
      </div>

      <div className="mt-10 flex justify-end gap-3">
        <Button variant="outline" size="lg" onClick={() => router.back()} disabled={sending}>
          취소
        </Button>
        <Button
          size="lg"
          onClick={send}
          loading={sending}
          disabled={selected.length === 0}
          rightIcon={<SendIcon />}
        >
          {selected.length === 0
            ? "상담 요청"
            : `${selected.length}명에게 상담 요청`}
        </Button>
      </div>
    </>
  );
}

interface RecommendationRowProps {
  item: RecommendationItem;
  checked: boolean;
  onToggle: () => void;
}

/** 추천 개발자 한 줄. 체크박스 · 이름 · 기술 · 세 지표 · 자세히. */
function RecommendationRow({ item, checked, onToggle }: Readonly<RecommendationRowProps>) {
  const { developer } = item;

  return (
    <div className="flex items-center gap-4 py-5">
      <Checkbox
        checked={checked}
        onChange={onToggle}
        aria-label={`${developer.name} 개발자 선택`}
        className="shrink-0"
      />

      <Avatar size="md" name={developer.name} className="shrink-0" />

      <div className="min-w-0 flex-1">
        <p className="truncate text-base font-bold text-fg">{developer.name} 개발자</p>
        {developer.techCategories.length > 0 && (
          <ul className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1">
            {developer.techCategories.map((tech, index) => (
              <li key={tech.codeId} className="flex items-center gap-2">
                {index > 0 && (
                  <span aria-hidden className="text-fg-disabled">
                    ·
                  </span>
                )}
                <Tag className="bg-transparent px-0">{tech.codeName}</Tag>
              </li>
            ))}
          </ul>
        )}
      </div>

      <dl className="hidden shrink-0 items-center gap-6 sm:flex">
        <Metric label="실력" value={developer.devRating.score?.toFixed(1) ?? "-"} />
        <Metric label="설명력" value={developer.explanatoryRating.score?.toFixed(1) ?? "-"} />
        <Metric label="완료" value={`${developer.contractCount}건`} />
      </dl>

      <Link
        href={ROUTES.developer(developer.developerId)}
        className="shrink-0 rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        자세히 ›
      </Link>
    </div>
  );
}

function Metric({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="text-center">
      <dd className="font-mono text-sm font-semibold text-fg tabular-nums">{value}</dd>
      <dt className="mt-0.5 text-xs text-fg-subtle">{label}</dt>
    </div>
  );
}

function SendIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M22 2 11 13M22 2l-7 20-4-9-9-4 20-7z" />
    </svg>
  );
}
