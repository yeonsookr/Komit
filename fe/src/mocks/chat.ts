/**
 * ⚠️ 임시 채팅 데이터. 실제 채팅 계약이 정해지면 통째로 지웁니다.
 *
 * 코밋 목 서버는 메시지를 다루지 않으므로(방 응답의 chatRefId 로 넘김) 화면을
 * 먼저 만들기 위해 FE 가 들고 있는 값입니다. 여기 있는 값을 근거로 화면 로직을
 * 짜지 마세요 — 방 순서나 갈래 판단은 목 서버의 방 API 가 이미 내려줍니다.
 *
 * chatRefId 는 목 서버의 방 데이터와 맞춰 둡니다 — 맡긴 의뢰가 room-91-chat ~
 * room-97-chat, 맡은 의뢰가 room-101-chat ~ room-103-chat 입니다.
 */
import type { ChatMessage } from "@/types/chat";

const MESSAGES: ChatMessage[] = [
  {
    messageId: "m-91-1",
    chatRefId: "room-91-chat",
    sender: "COUNTERPART",
    authorName: "이준",
    body: "요청서 확인했습니다. 관리자 API 인증은 middleware에서 한 번에 처리 가능해 보입니다. 회원탈퇴는 데이터 보관 정책을 어떻게 하실지 알려주세요.",
    sentAt: "2026-08-20T10:14:00+09:00",
  },
  {
    messageId: "m-91-2",
    chatRefId: "room-91-chat",
    sender: "ME",
    authorName: "김미미",
    body: "보관은 30일 후 완전 삭제로 부탁드려요",
    sentAt: "2026-08-20T10:32:00+09:00",
  },
  {
    messageId: "m-91-3",
    chatRefId: "room-91-chat",
    sender: "COUNTERPART",
    authorName: "이준",
    body: "(상담) 알겠습니다. 계약서 보내드릴게요.",
    sentAt: "2026-08-20T11:02:00+09:00",
  },
  {
    messageId: "m-92-1",
    chatRefId: "room-92-chat",
    sender: "COUNTERPART",
    authorName: "이준",
    body: "(컨설팅) 범위가 아직 좀 넓어 보입니다. 30분만 통화하면서 정리하면 견적이 정확해질 것 같아요.",
    sentAt: "2026-08-22T11:40:00+09:00",
  },
  {
    messageId: "m-93-1",
    chatRefId: "room-93-chat",
    sender: "COUNTERPART",
    authorName: "이준",
    body: "1차 작업 시작했습니다.",
    sentAt: "2026-08-25T11:05:00+09:00",
  },
  {
    messageId: "m-93-2",
    chatRefId: "room-93-chat",
    sender: "COUNTERPART",
    authorName: "이준",
    body: "(진행 중) 그 부분은 코드 보고 말씀드릴게요",
    sentAt: "2026-08-28T14:20:00+09:00",
  },

  {
    messageId: "m-104-1",
    chatRefId: "room-104-chat",
    sender: "COUNTERPART",
    authorName: "이준",
    body: "(완료) 네 건 다 통과 확인 감사합니다. 정산도 끝났습니다.",
    sentAt: "2026-09-04T17:05:00+09:00",
  },

  // 맡은 의뢰 — 내가 개발자로 들어간 방(101~103). 상대는 의뢰인 박준호입니다.
  {
    messageId: "m-101-1",
    chatRefId: "room-101-chat",
    sender: "COUNTERPART",
    authorName: "박준호",
    body: "예약 페이지가 자꾸 먹통이 됩니다. 진단서 보내드렸어요.",
    sentAt: "2026-08-24T10:10:00+09:00",
  },
  {
    messageId: "m-101-2",
    chatRefId: "room-101-chat",
    sender: "ME",
    authorName: "김미미",
    body: "(상담) 확인했습니다. 예약 저장 쪽부터 보면 될 것 같아요.",
    sentAt: "2026-08-24T10:41:00+09:00",
  },
  {
    messageId: "m-103-1",
    chatRefId: "room-103-chat",
    sender: "ME",
    authorName: "김미미",
    body: "계약서 보냈습니다. 확인 부탁드려요.",
    sentAt: "2026-08-28T09:20:00+09:00",
  },
  {
    messageId: "m-103-2",
    chatRefId: "room-103-chat",
    sender: "COUNTERPART",
    authorName: "박준호",
    body: "(진행 중) 네, 내용 보고 서명할게요.",
    sentAt: "2026-08-29T11:15:00+09:00",
  },

  // 아직 서명하지 않은 방. 105 는 내가 개발자, 106 은 내가 의뢰인입니다.
  {
    messageId: "m-105-1",
    chatRefId: "room-105-chat",
    sender: "ME",
    authorName: "김미미",
    body: "계약서 보냈습니다. 65만원에 21일로 잡았어요.",
    sentAt: "2026-08-22T16:10:00+09:00",
  },
  {
    messageId: "m-105-2",
    chatRefId: "room-105-chat",
    sender: "COUNTERPART",
    authorName: "박준호",
    body: "(계약 전) 확인했습니다. 내용 보고 서명하겠습니다.",
    sentAt: "2026-08-22T16:40:00+09:00",
  },
  {
    messageId: "m-106-1",
    chatRefId: "room-106-chat",
    sender: "COUNTERPART",
    authorName: "이준",
    body: "계약서 보냈습니다. 확인하시고 서명해 주세요.",
    sentAt: "2026-08-22T16:10:00+09:00",
  },
  {
    messageId: "m-106-2",
    chatRefId: "room-106-chat",
    sender: "ME",
    authorName: "김미미",
    body: "(계약 전) 네, 읽어보고 오늘 중으로 서명할게요.",
    sentAt: "2026-08-22T16:35:00+09:00",
  },

  // 예치까지 끝나고 아직 착수하지 않은 방입니다.
  {
    messageId: "m-108-1",
    chatRefId: "room-108-chat",
    sender: "COUNTERPART",
    authorName: "박준호",
    body: "(착수 전) 예치까지 마쳤습니다. 착수해 주세요.",
    sentAt: "2026-08-23T10:30:00+09:00",
  },
];

/** 한 방의 메시지를 보낸 순서로 돌려줍니다. */
export function findChatMessages(chatRefId: string): ChatMessage[] {
  return MESSAGES.filter((message) => message.chatRefId === chatRefId).sort((a, b) =>
    a.sentAt.localeCompare(b.sentAt),
  );
}
