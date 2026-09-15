import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Container } from "@/components/layouts";
import { Badge, Breadcrumb, PageHeader } from "@/components/common";
import {
  RequestSummaryBar,
  RequestSummaryEmpty,
  RoomConversation,
} from "@/components/domains/room";
import { getChatMessages, getMe, getRoom } from "@/lib/api/services";
import { ApiError } from "@/types/api";
import type { RoomDetail, RoomType } from "@/types/room";
import type { Me } from "@/types/user";

/** 방 용도별 표기. 배지와 머리말에 씁니다. */
const ROOM_TYPE_LABEL: Record<RoomType, string> = {
  INQUIRY: "상담",
  CONSULTING: "컨설팅",
  DEVELOPMENT: "개발",
};

/**
 * 내가 이 방에 개발자로 들어갔는지.
 * 목 명세대로 developerId 하나만 견줍니다 — 같으면 '맡은 의뢰'입니다.
 */
function isReceivedRequest(room: RoomDetail, me: Me): boolean {
  return me.developerId !== null && me.developerId === room.developerId;
}

/** 방과 내 정보를 함께 읽습니다. 없는 방이면 404 화면으로 보냅니다. */
async function loadRoom(roomId: number) {
  try {
    const [room, me] = await Promise.all([getRoom(roomId), getMe()]);
    return { room, me };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ roomId: string }>;
}): Promise<Metadata> {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id)) return { title: "방을 찾을 수 없습니다 | Komit" };

  const { room, me } = await loadRoom(id);
  const title = isReceivedRequest(room, me)
    ? `${room.counterpartName}와 ${ROOM_TYPE_LABEL[room.type]}`
    : room.projectName;

  return {
    title: `${title} | Komit`,
    description: `${room.projectName} · ${room.counterpartName}님과의 ${ROOM_TYPE_LABEL[room.type]} 대화입니다.`,
  };
}

export default async function RoomPage({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  const id = Number(roomId);
  if (!Number.isInteger(id) || id <= 0) notFound();

  const { room, me } = await loadRoom(id);
  const messages = await getChatMessages(room.chatRefId);

  const received = isReceivedRequest(room, me);
  const typeLabel = ROOM_TYPE_LABEL[room.type];
  // 맡은 의뢰는 상대(의뢰인)가 제목, 맡긴 의뢰는 내 프로젝트가 제목입니다.
  const title = received ? `${room.counterpartName}와 ${typeLabel}` : room.projectName;

  return (
    <Container className="py-8 sm:py-10">
      <PageHeader
        breadcrumb={<Breadcrumb items={[received ? "맡은 의뢰" : "맡긴 의뢰", typeLabel]} />}
        title={title}
        meta={
          <span>
            {received ? room.counterpartName : `${room.counterpartName} 개발자`} · {typeLabel}
          </span>
        }
        badge={room.status === "CLOSED" ? <Badge tone="neutral">마무리됨</Badge> : undefined}
      />

      <div className="mt-6">
        {room.request ? (
          <RequestSummaryBar
            request={room.request}
            href={`/projects/${room.projectId}/request`}
          />
        ) : (
          <RequestSummaryEmpty />
        )}
      </div>

      {/* 갈래는 의뢰인이 정합니다 — 개발자에게는 버튼을 보이지 않습니다. */}
      <RoomConversation
        room={room}
        initialMessages={messages}
        myName={me.name}
        canConvert={!received}
      />
    </Container>
  );
}
