/** 알림 종류. 목의 `NOTIFICATION_TYPE` 코드값을 그대로 씁니다. */
export type NotificationType = "공지" | "착수알림" | "승인알림" | "예약알림톡";

/** 확인하기를 눌렀을 때 가리키는 업무 대상입니다. */
export type NotificationTargetType = "ANNOUNCEMENT" | "CONTRACT" | "ISSUE";

/** 알림 목록 한 줄. */
export interface Notification {
  notificationId: number;
  type: NotificationType;
  title: string;
  content: string;
  targetType: NotificationTargetType;
  targetId: number | null;
  /** 계약 알림을 방 안에서 열 때 쓰는 방 식별자입니다. */
  roomId: number | null;
  isRead: boolean;
  createdAt: string;
}

/** 알림 목록. `unreadCount` 는 현재 페이지가 아니라 전체 안 읽은 수입니다. */
export interface NotificationList {
  content: Notification[];
  unreadCount: number;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface NotificationListParams {
  unreadOnly?: boolean;
  page?: number;
  size?: number;
}
