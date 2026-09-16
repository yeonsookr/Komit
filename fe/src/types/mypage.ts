/**
 * 마이페이지 타입. 목 Swagger `/service/docs` 영역 8 의 MyPageResponse 와
 * 필드가 같습니다. 명세에 없는 필드를 더하지 않습니다.
 */

/** 계정 칸. 프로필 사진 · 이름 · 카카오 계정. */
export interface MyAccount {
  name: string;
  /** 카카오 계정 이메일. 동의하지 않았으면 null. */
  email: string | null;
  /**
   * 화면에 그대로 거는 사진 주소. 직접 올린 사진이 있으면 그 주소, 없으면 카카오 사진,
   * 둘 다 없으면 null 이고 그때 화면은 이름 첫 글자를 씁니다.
   *
   * 한동안만 쓸 수 있는 임시 주소입니다. 저장해 두고 오래 쓰지 않습니다.
   */
  profileImageUrl: string | null;
}

/** 환불과 대금을 받는 계좌. 사람마다 하나입니다. (R-ACC-04) */
export interface SettlementAccount {
  accountId: number;
  bankName: string;
  /** "···· 8842" 처럼 이미 가려진 값입니다. 화면이 따로 가리지 않습니다. */
  maskedNumber: string;
  accountHolder: string;
}

/** 내가 등록한 프로젝트 한 줄. */
export interface MyProject {
  projectId: number;
  name: string;
  /** 가장 최근 진단서. 아직 진단이 없으면 null 이고 화면은 「사전점검 하기」를 냅니다. */
  latestDiagnosisId: number | null;
  latestDiagnosedAt: string | null;
  /** 이 프로젝트로 연 방 수. */
  roomCount: number;
}

/** 개발자 프로필 칸. 프로필이 없는 사람에게는 null 로 와서 칸이 통째로 빠집니다. */
export interface MyDeveloper {
  /** 개발자 식별자(사용자 식별자가 아님). */
  developerId: number;
  reviewCount: number;
  /** 아직 하지 않은 컨설팅 세션 수. 화면의 "잡힌 세션 N건". */
  upcomingSessionCount: number;
}

/** 알림 수신 설정. 거짓이면 방 소식과 확인 요청을 보내지 않습니다. */
export interface NotificationSetting {
  notificationEnabled: boolean;
}

/** 마이페이지 한 판. 화면 전체가 이 한 번의 응답으로 그려집니다. */
export interface MyPage {
  account: MyAccount;
  /** 등록하지 않았으면 null 이고 화면이 그 칸을 등록 안내로 바꿉니다. */
  settlementAccount: SettlementAccount | null;
  /** 없으면 빈 배열. */
  projects: MyProject[];
  /** null 이면 개발자 프로필 칸을 그리지 않습니다. */
  developer: MyDeveloper | null;
  notificationSetting: NotificationSetting;
}

/** 이름 바꾸기. 20자까지 받습니다. */
export interface UpdateMyNameBody {
  name: string;
}

/** 알림 켜고 끄기. */
export interface UpdateNotificationSettingBody {
  notificationEnabled: boolean;
}
