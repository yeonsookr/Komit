import "server-only";

import { backendApi } from "../server";
import { BACKEND } from "../endpoints";
import { bearerHeaders } from "@/lib/auth/session";
import type {
  MyPage,
  UpdateMyNameBody,
  UpdateNotificationSettingBody,
} from "@/types/mypage";
import type { Me } from "@/types/user";

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
 * 응답으로 바뀐 내 정보가 옵니다. 헤더도 이 이름을 쓰므로 화면은 받은 값으로
 * 인증 스토어를 갱신해야 합니다. 갱신하지 않으면 헤더에 옛 이름이 남습니다.
 */
export async function updateMyName(body: UpdateMyNameBody): Promise<Me> {
  const { data } = await backendApi().patch<Me>(BACKEND.ME, body, await bearerHeaders());
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
