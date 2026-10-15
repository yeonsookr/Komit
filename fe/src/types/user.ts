/** 모든 화면의 헤더를 채우는 내 정보. 목 Swagger 의 MeResponse 와 같습니다. */
export interface Me {
  userId: number;
  name: string;
  /** 올린 사진이 없으면 null. 그때 화면은 이름 첫 글자를 씁니다. */
  profileImageFileId: number | null;
  /** 직접 올린 사진, 카카오 사진 순서로 고른 주소. 둘 다 없으면 null. */
  profileImageUrl: string | null;
  hasDevProfile: boolean;
  /** 개발자로 전환하지 않았으면 null. 방의 developerId 와 견줍니다. */
  developerId: number | null;
  unreadNotificationCount: number;
}
