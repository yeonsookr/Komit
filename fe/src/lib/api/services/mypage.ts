import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import { bearerHeaders } from "@/lib/auth/session";
import type {
  MyAccount,
  MyPage,
  UpdateMyNameBody,
  UpdateNotificationSettingBody,
  WithdrawalCheck,
  WithdrawalResult,
} from "@/types/mypage";

/**
 * 마이페이지 한 판.
 *
 * 계정·계좌·프로젝트·개발자 프로필·알림 설정이 한 응답에 들어 있어 화면이 한 번만
 * 부릅니다. 로그인하지 않았으면 401 입니다.
 */
export async function getMyPage(): Promise<MyPage> {
  const { data } = await backendApi().get<MyPage>(BACKEND.MYPAGE, await bearerHeaders());
  return data;
}

/**
 * 이름 바꾸기. 20자까지입니다.
 *
 * 돌아오는 것은 마이페이지 계정 칸(`MyAccount`)이지 헤더가 쓰는 내 정보(`Me`)가
 * 아닙니다. 알림 개수도 사용자 식별자도 들어 있지 않으므로 이 값을 인증 스토어에
 * 그대로 넣으면 안 됩니다. 헤더는 `/api/users/me` 를 다시 읽어 맞춥니다.
 */
export async function updateMyName(body: UpdateMyNameBody): Promise<MyAccount> {
  const { data } = await backendApi().patch<MyAccount>(
    BACKEND.ME,
    body,
    await bearerHeaders(),
  );
  return data;
}

/** 알림톡을 받을지 정합니다. 바뀐 설정이 그대로 옵니다. */
export async function updateNotificationSetting(
  body: UpdateNotificationSettingBody,
): Promise<UpdateNotificationSettingBody> {
  const { data } = await backendApi().patch<UpdateNotificationSettingBody>(
    BACKEND.NOTIFICATION_SETTING,
    body,
    await bearerHeaders(),
  );
  return data;
}

/**
 * 탈퇴 전 확인.
 *
 * 지금 탈퇴할 수 있는지와 무엇이 막고 있는지를 내려줍니다. 화면은 `canWithdraw` 가
 * 거짓이면 탈퇴 버튼을 잠급니다. (R-ACC-05)
 */
export async function checkWithdrawal(): Promise<WithdrawalCheck> {
  const { data } = await backendApi().get<WithdrawalCheck>(
    BACKEND.WITHDRAWAL,
    await bearerHeaders(),
  );
  return data;
}

/**
 * 회원 탈퇴. 되돌릴 수 없습니다.
 *
 * 행을 지우지 않고 삭제 표시만 켜므로 `DELETE` 가 아니라 `POST` 입니다.
 * 화면이 버튼을 잠갔더라도 서버가 같은 조건을 다시 봅니다 — 막히면 409 입니다.
 */
export async function withdraw(): Promise<WithdrawalResult> {
  const { data } = await backendApi().post<WithdrawalResult>(
    BACKEND.WITHDRAWAL,
    undefined,
    await bearerHeaders(),
  );
  return data;
}
