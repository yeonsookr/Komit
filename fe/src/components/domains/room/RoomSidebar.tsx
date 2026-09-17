"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/cn";
import { Badge, SegmentedTabs } from "@/components/common";
import {
  filterBySide,
  groupByProject,
  isReceived,
  ROOM_TYPE_LABEL,
  type RequestSide,
} from "@/lib/rooms";
import type { RoomSummary } from "@/types/room";
import type { Me } from "@/types/user";

/** 방 용도별 배지 색. 상담은 연한 회색, 컨설팅·개발은 브랜드 색을 씁니다. */
const TYPE_TONE = {
  INQUIRY: "success",
  CONSULTING: "warning",
  DEVELOPMENT: "brand",
} as const;

/** 목록 한 줄에 붙는 마지막 대화 요약. */
export interface RoomPreview {
  /** 마지막 메시지 한 줄. 아직 대화가 없으면 비웁니다. */
  body?: string;
  /** "지금 · 12분 전 · 어제 · 8/20". 서버에서 계산해 내려보냅니다. */
  time?: string;
}

export interface RoomSidebarProps {
  rooms: RoomSummary[];
  me: Me;
  /** chatRefId 별 미리보기. 없으면 이름만 보입니다. */
  previews?: Record<string, RoomPreview | undefined>;
}

/**
 * RoomSidebar — 의뢰 화면 좌측 스레드 목록(320px).
 *
 * 레이아웃에 놓이므로 방을 옮겨도 다시 그려지지 않습니다. 현재 방은 URL 에서
 * 읽어 활성 표시하고, 목록 자체는 서버에서 받은 값을 그대로 씁니다.
 */
export function RoomSidebar({ rooms, me, previews }: Readonly<RoomSidebarProps>) {
  // 지금 보고 있는 방. 이 레이아웃 기준의 세그먼트는 "rooms" 라서 경로에서 직접 읽습니다.
  const pathname = usePathname();
  const activeRoomId = Number(/^\/rooms\/(\d+)/.exec(pathname)?.[1]);
  /** 목록 화면(`/rooms`)에서는 고른 방이 없습니다. */
  const hasActiveRoom = Number.isFinite(activeRoomId);

  /**
   * 처음에는 '맡긴 의뢰'를 엽니다. 개발자로도 등록한 사람이라도 이 화면에 들어오는
   * 경로는 대개 내가 맡긴 의뢰 쪽입니다.
   *
   * 다만 이미 어떤 방을 보고 있다면 그 방이 있는 쪽을 엽니다 — 새로고침이나 링크로
   * 바로 들어왔을 때 지금 보는 방이 목록에 없으면 안 됩니다.
   */
  const activeRoom = rooms.find((room) => room.roomId === activeRoomId);
  const [side, setSide] = React.useState<RequestSide>(
    activeRoom !== undefined && isReceived(activeRoom, me) ? "RECEIVED" : "SENT",
  );

  const groups = React.useMemo(
    () => groupByProject(filterBySide(rooms, me, side)),
    [rooms, me, side],
  );

  /**
   * 좁은 화면에서 목록을 펼칠지.
   *
   * 방에 들어와 있으면 접습니다 — 펼쳐 두면 대화가 목록 아래로 밀려 정작 대화를
   * 보지 못합니다. 반대로 목록 화면(`/rooms`)에서는 펼칩니다. 거기서 접으면
   * 고를 것이 보이지 않아 빈 화면이 됩니다. lg 이상에서는 이 값과 무관하게 늘 펼칩니다.
   */
  const [open, setOpen] = React.useState(!hasActiveRoom);
  const roomCount = React.useMemo(
    () => filterBySide(rooms, me, side).length,
    [rooms, me, side],
  );

  // 방을 고르면 접고, 목록으로 돌아오면 다시 펼칩니다.
  React.useEffect(() => setOpen(!hasActiveRoom), [pathname, hasActiveRoom]);

  return (
    <nav aria-label="의뢰 목록" className="flex h-full flex-col py-4 lg:py-6">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        className="flex items-center justify-between rounded-lg px-3 py-2.5 text-sm font-semibold text-fg hover:bg-surface lg:hidden"
      >
        <span>의뢰 목록 {roomCount > 0 && <span className="text-fg-subtle">{roomCount}</span>}</span>
        <span aria-hidden className="text-fg-subtle">
          {open ? "▲" : "▼"}
        </span>
      </button>

      <div className={cn("flex-col gap-4 pt-2 lg:flex lg:pt-0", open ? "flex" : "hidden")}>
      <SegmentedTabs
        fullWidth
        aria-label="의뢰 구분"
        value={side}
        onChange={setSide}
        items={[
          { value: "SENT", label: "맡긴 의뢰" },
          { value: "RECEIVED", label: "맡은 의뢰" },
        ]}
      />

      {groups.length === 0 ? (
        <p className="px-1 py-8 text-center text-[13px] text-fg-muted">
          {side === "SENT" ? "아직 맡긴 의뢰가 없습니다." : "아직 맡은 의뢰가 없습니다."}
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {groups.map((group) => (
            <li key={group.projectId}>
              <p className="rounded-lg bg-surface px-3 py-2 text-[13px] font-semibold text-fg">
                {group.projectName}
              </p>

              <ul className="mt-1 flex flex-col">
                {group.rooms.map((room) => (
                  <li key={room.roomId}>
                    <RoomLink
                      room={room}
                      active={room.roomId === activeRoomId}
                      preview={previews?.[room.chatRefId]}
                    />
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}
      </div>
    </nav>
  );
}

interface RoomLinkProps {
  room: RoomSummary;
  active: boolean;
  preview?: RoomPreview;
}

/** 목록의 방 한 줄. 현재 보고 있는 방은 왼쪽에 보라 막대가 섭니다. */
function RoomLink({ room, active, preview }: Readonly<RoomLinkProps>) {
  // 마무리된 방은 마지막 말 대신 상태를 알립니다.
  const secondary = room.status === "CLOSED" ? "마무리됨" : preview?.body;

  return (
    <Link
      href={`/rooms/${room.roomId}`}
      aria-current={active ? "page" : undefined}
      className={cn(
        "flex items-start gap-2.5 rounded-lg px-3 py-2.5 transition-colors",
        "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary",
        active ? "bg-surface" : "hover:bg-surface",
      )}
    >
      <span
        aria-hidden
        className={cn(
          "mt-0.5 h-8 w-0.5 shrink-0 rounded-full",
          active ? "bg-primary" : "bg-transparent",
        )}
      />

      <Badge tone={TYPE_TONE[room.type]} shape="square" size="sm" className="mt-0.5 shrink-0">
        {ROOM_TYPE_LABEL[room.type]}
      </Badge>

      <span className="min-w-0 flex-1">
        <span className="flex items-baseline gap-2">
          <span className="min-w-0 flex-1 truncate text-sm font-semibold text-fg">
            {room.counterpartName}
          </span>
          {preview?.time && (
            <span className="shrink-0 font-mono text-[11px] text-fg-subtle tabular-nums">
              {preview.time}
            </span>
          )}
        </span>
        {secondary && (
          <span className="mt-0.5 block truncate text-[13px] text-fg-subtle">{secondary}</span>
        )}
      </span>
    </Link>
  );
}
