import type { SettlementAccount } from "./mypage";

/** POST /api/accounts/member-account — 회원가입 때 발급된 내 계좌. (R-ACC-14) */
export interface MemberAccount {
  /** 에스크로가 계좌를 연 은행코드. 데모는 001 한 곳뿐입니다. */
  bankCode: string;
  /** 가리지 않은 계좌번호 16자리. 본인에게만 내려옵니다. */
  accountNumber: string;
}

/** POST /api/accounts/verifications — 1원 보내기. */
export interface RequestAccountVerificationBody {
  bankCodeId: number;
  /** 하이픈 없는 계좌번호. */
  accountNumber: string;
  accountHolder: string;
}

/** 1원을 보낸 뒤 이어서 확인할 계좌와 확인 건. */
export interface AccountVerification {
  verificationId: number;
  bankName: string;
  /** 화면에 그대로 보여주는 가려진 계좌번호. */
  maskedNumber: string;
  accountHolder: string;
  expiresAt: string;
}

/** POST /api/accounts/verifications/{verificationId}/confirm — 확인 번호 제출. */
export interface ConfirmAccountVerificationBody {
  /** 앞자리 0을 보존하는 네 자리 문자열. */
  authCode: string;
}

/** 계좌 확인이 끝나면 등록된 계좌가 돌아옵니다. */
export type ConfirmAccountVerificationResponse = SettlementAccount;
