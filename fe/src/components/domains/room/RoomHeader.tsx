import { Badge, Breadcrumb, PageHeader } from "@/components/common";
import { developmentStageLabel, isUnderway } from "@/lib/contract";
import { formatShortDate } from "@/lib/format";
import { isReceived, ROOM_TYPE_LABEL } from "@/lib/rooms";
import { confirmedSession } from "@/lib/session";
import { ROUTES } from "@/lib/routes";
import type { Contract } from "@/types/contract";
import type { RoomDetail } from "@/types/room";
import type { Me } from "@/types/user";
import { ContractSummaryBar } from "./ContractSummaryBar";
import { RequestSummaryBar, RequestSummaryEmpty } from "./RequestSummaryBar";
import { RoomTabs } from "./RoomTabs";
import { SessionSummaryBar } from "./SessionSummaryBar";

export interface RoomHeaderProps {
  room: RoomDetail;
  me: Me;
  /** 이 방의 계약서. 아직 쓰지 않았으면 null 입니다. */
  contract?: Contract | null;
  /**
   * 요약이 갱신된 시각. 요약 탭만 아는 값이라 그 화면에서만 넘깁니다.
   * 나머지 탭은 세션이 끝난 시각으로 대신합니다(`SessionSummaryBar`).
   */
  summaryUpdatedAt?: string | null;
}

/**
 * RoomHeader — 방 안 모든 탭이 함께 쓰는 머리.
 *   브레드크럼 · 제목 · 상태 배지 · 탭바 · 요청서·계약서 요약 바
 *
 * 탭마다 이 한 줄만 놓으면 됩니다. 레이아웃으로 올리지 않은 것은 채팅 화면이
 * 이미 자기 자리에서 방을 읽고 있어, 레이아웃을 끼우면 같은 머리가 두 번
 * 그려지기 때문입니다.
 */
export function RoomHeader({
  room,
  me,
  contract = null,
  summaryUpdatedAt = null,
}: Readonly<RoomHeaderProps>) {
  const received = isReceived(room, me);
  const typeLabel = ROOM_TYPE_LABEL[room.type];
  const counterpart = received ? room.counterpartName : `${room.counterpartName} 개발자`;

  // 배지는 계약이 어디까지 왔는지를 적습니다. 계약이 없는 상담방은 방 상태로 대신합니다.
  const stage = room.type === "DEVELOPMENT" ? developmentStageLabel(contract) : null;
  const badge =
    stage ?? (room.status === "CLOSED" ? "마무리됨" : null);

  // 착수 전까지는 보여줄 진행이 없어 언제 열린 방인지를 적습니다.
  const milestones = contract?.development?.milestones.length ?? 0;
  const detail = isUnderway(contract)
    ? `작업 ${milestones}건`
    : `${formatShortDate(room.openedAt)} 개설`;

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb items={[received ? "맡은 의뢰" : "맡긴 의뢰", typeLabel]} />}
        title={room.projectName}
        meta={
          <span>
            {counterpart} · {room.type === "DEVELOPMENT" ? detail : typeLabel}
          </span>
        }
        badge={badge === null ? undefined : <Badge tone="neutral">{badge}</Badge>}
      />

      <div className="mt-5 empty:mt-0">
        <RoomTabs roomId={room.roomId} roomType={room.type} />
      </div>

      <div className="mt-6 flex flex-col gap-2">
        {room.request ? (
          <RequestSummaryBar
            request={room.request}
            href={ROUTES.checkupRequest(room.request.diagnosisId)}
          />
        ) : (
          <RequestSummaryEmpty />
        )}

        {/* 계약은 갈래가 정해진 뒤에 생깁니다. 무료 상담방에는 줄을 달지 않습니다. */}
        {room.type !== "INQUIRY" && <ContractSummaryBar contract={contract} />}

        {/* 컨설팅에서 손에 남는 것은 계약서와 세션 요약 둘입니다. */}
        {room.type === "CONSULTING" && (
          <SessionSummaryBar
            session={confirmedSession(contract)}
            summaryUpdatedAt={summaryUpdatedAt}
          />
        )}
      </div>
    </>
  );
}
