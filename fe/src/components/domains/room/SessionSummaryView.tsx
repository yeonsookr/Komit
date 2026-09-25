"use client";

import { useRouter } from "next/navigation";
import * as React from "react";
import { Badge, Card } from "@/components/common";
import { Checkbox } from "@/components/common";
import { Button } from "@/components/buttons";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { formatShortDateTime } from "@/lib/format";
import { ApiError } from "@/types/api";
import type { ApplySummaryChangesRequest, SessionSummary, SummaryItem } from "@/types/contract";

/** 변경 후보가 요청서에 어떻게 들어가는지. */
const KIND_LABEL = {
  UPDATE: "요구사항 수정",
  ADD: "새 요구사항",
} as const;

export interface SessionSummaryViewProps {
  sessionId: number;
  summary: SessionSummary;
  /** 의뢰인만 요청서를 고칩니다. 개발자에게는 고르는 자리를 주지 않습니다. */
  canApply: boolean;
}

/** 변경 후보 칸의 안내. 후보가 없을 때 「모두 반영했다」고 적으면 반영한 것이 있는 줄 안다. */
function changesNote(total: number, pending: number): string | undefined {
  if (total === 0) return undefined;
  return pending === 0 ? "모두 요청서에 반영했습니다" : "고른 것만 요청서에 들어갑니다";
}

/**
 * SessionSummaryView — 컨설팅 세션 요약 (`4_의뢰인_요약`).
 *
 * 네 칸입니다 — 논의한 것 · 결정된 것 · 요청서 변경 후보 · 다음에 할 것.
 *
 * **와이어프레임과 한 군데 다릅니다.** 그림에서는 「결정된 것」 카드마다 그에 딸린
 * 변경 후보가 붙어 있지만, 응답의 `decided` 와 `changes` 는 서로를 가리키는 값이
 * 없습니다(`SessionSummaryResponse`). 없는 연결을 화면에서 지어내면 엉뚱한 결정에
 * 엉뚱한 후보가 붙으므로, 후보를 제 칸으로 빼고 「결정된 것」 바로 아래에 두었습니다.
 *
 * 반영은 고른 것만 들어갑니다. 요약을 읽는 것과 요청서를 고치는 것은 다른 일이라,
 * 세션에서 정해졌다고 저절로 요청서가 바뀌지는 않습니다.
 */
export function SessionSummaryView({
  sessionId,
  summary: initial,
  canApply,
}: Readonly<SessionSummaryViewProps>) {
  const router = useRouter();
  const showToast = useToast();
  const [summary, setSummary] = React.useState(initial);
  const [picked, setPicked] = React.useState<number[]>([]);
  const [applying, setApplying] = React.useState(false);

  /*
    서버에서 **더 새 요약**이 오면 그 값으로 맞춥니다.

    객체가 아니라 `updatedAt` 이 달라졌을 때만 갈아 끼웁니다. 서버 컴포넌트는 다시
    그릴 때마다 새 객체를 내려보내므로, 객체만 보면 반영 직후의 `router.refresh()` 가
    방금 받은 응답을 도로 덮어 「반영됨」이 풀립니다.
  */
  const seenUpdatedAt = React.useRef(initial.updatedAt);
  React.useEffect(() => {
    if (seenUpdatedAt.current === initial.updatedAt) return;
    seenUpdatedAt.current = initial.updatedAt;
    setSummary(initial);
    setPicked([]);
  }, [initial]);

  const pending = summary.changes.filter((change) => !change.isApplied);

  const toggle = (changeId: number) =>
    setPicked((prev) =>
      prev.includes(changeId) ? prev.filter((id) => id !== changeId) : [...prev, changeId],
    );

  const apply = async () => {
    if (picked.length === 0 || applying) return;
    setApplying(true);

    try {
      const body: ApplySummaryChangesRequest = { changeIds: picked };
      const { data } = await clientApi.post<SessionSummary>(
        INTERNAL.SESSION_SUMMARY_CHANGES(sessionId),
        body,
      );
      setSummary(data);
      setPicked([]);
      showToast({
        tone: "success",
        title: `${picked.length}건을 요청서에 반영했습니다`,
        description: "개발 계약으로 이어갈 때 이 요청서가 쓰입니다.",
      });
      router.refresh();
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "요청서에 반영하지 못했습니다.",
      });
    } finally {
      setApplying(false);
    }
  };

  return (
    <div className="mt-8">
      <h2 className="text-lg font-bold text-fg">컨설팅 요약</h2>
      <p className="mt-1 text-[13px] text-fg-subtle">
        녹음 내용을 글로 옮겨 정리했습니다 ·{" "}
        {formatShortDateTime(summary.updatedAt)}
      </p>

      <Block title="논의한 것" count={summary.discussed.length}>
        <ul className="flex flex-col gap-2.5">
          {summary.discussed.map((item) => (
            <li key={item.title} className="flex gap-2 text-sm leading-6 text-fg">
              <span aria-hidden className="text-fg-subtle">
                ·
              </span>
              <span>
                {item.title}
                {item.detail && <span className="text-fg-muted"> — {item.detail}</span>}
              </span>
            </li>
          ))}
        </ul>
      </Block>

      <Block title="결정된 것" count={summary.decided.length}>
        <ItemCards items={summary.decided} />
      </Block>

      <Block
        title="요청서 변경 후보"
        count={summary.changes.length}
        note={changesNote(summary.changes.length, pending.length)}
      >
        <div className="flex flex-col gap-2.5">
          {summary.changes.map((change) => (
            <Card key={change.changeId} variant="outline" padding="sm">
              <div className="flex items-start justify-between gap-3">
                {canApply && !change.isApplied ? (
                  <Checkbox
                    checked={picked.includes(change.changeId)}
                    onChange={() => toggle(change.changeId)}
                    label={<span className="font-semibold">{change.title}</span>}
                    description={change.detail}
                  />
                ) : (
                  <div className="flex flex-col gap-0.5">
                    <p className="text-sm font-semibold text-fg">{change.title}</p>
                    {change.detail && (
                      <p className="text-[13px] leading-6 text-fg-subtle">{change.detail}</p>
                    )}
                  </div>
                )}

                <Badge tone={change.isApplied ? "success" : "neutral"}>
                  {change.isApplied ? "반영됨" : KIND_LABEL[change.kind]}
                </Badge>
              </div>
            </Card>
          ))}
        </div>

        {canApply && pending.length > 0 && (
          <div className="mt-4 flex justify-end">
            <Button onClick={apply} disabled={picked.length === 0} loading={applying}>
              {picked.length === 0 ? "반영할 후보 고르기" : `${picked.length}건 요청서에 반영`}
            </Button>
          </div>
        )}
      </Block>

      <Block title="다음에 할 것" count={summary.nextActions.length}>
        <ItemCards items={summary.nextActions} />
      </Block>
    </div>
  );
}

/** 요약의 한 칸. 제목 · 건수 · 짧은 안내를 같은 모양으로 답니다. */
function Block({
  title,
  count,
  note,
  children,
}: Readonly<{
  title: string;
  count: number;
  note?: string;
  children: React.ReactNode;
}>) {
  return (
    <section className="mt-7">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h3 className="text-sm font-bold text-fg">
          {title} <span className="font-normal text-fg-subtle">{count}건</span>
        </h3>
        {note && <p className="text-[13px] text-fg-subtle">{note}</p>}
      </div>

      <div className="mt-3">
        {count === 0 ? <p className="text-[13px] text-fg-subtle">적힌 것이 없습니다.</p> : children}
      </div>
    </section>
  );
}

function ItemCards({ items }: Readonly<{ items: SummaryItem[] }>) {
  return (
    <div className="flex flex-col gap-2.5">
      {items.map((item) => (
        <Card key={item.title} variant="outline" padding="sm">
          <p className="text-sm font-semibold text-fg">{item.title}</p>
          {item.detail && (
            <p className="mt-1 text-[13px] leading-6 text-fg-muted">{item.detail}</p>
          )}
        </Card>
      ))}
    </div>
  );
}
