"use client";

import * as React from "react";
import { Button } from "@/components/buttons";
import { KeyValueRow } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ApiError } from "@/types/api";
import type { NotificationSetting } from "@/types/mypage";
import { MyPageSection } from "./MyPageSection";

export interface NotificationSectionProps {
  setting: NotificationSetting;
}

/**
 * NotificationSection — 알림 칸.
 *
 * 카카오 알림톡을 받을지 끄고 켭니다. 끄면 방 소식과 확인 요청을 보내지 않습니다.
 * 값을 바꾸는 칸이라 클라이언트 컴포넌트입니다.
 *
 * 화면이 켜짐·꺼짐을 스스로 들고 있습니다. 요청이 끝날 때까지 기다렸다 바꾸면
 * 누른 뒤 한참 그대로라 눌린 건지 알기 어렵기 때문입니다. 실패하면 되돌립니다.
 */
export function NotificationSection({ setting }: Readonly<NotificationSectionProps>) {
  const showToast = useToast();
  const [enabled, setEnabled] = React.useState(setting.notificationEnabled);
  const [saving, setSaving] = React.useState(false);

  // 서버에서 다시 읽어 오면 그 값으로 맞춥니다.
  React.useEffect(() => setEnabled(setting.notificationEnabled), [setting.notificationEnabled]);

  const toggle = async () => {
    if (saving) return;

    const next = !enabled;
    setEnabled(next);
    setSaving(true);

    try {
      await clientApi.patch(INTERNAL.NOTIFICATION_SETTING, { notificationEnabled: next });
      showToast({
        tone: "success",
        title: next ? "알림톡을 받습니다." : "알림톡을 받지 않습니다.",
      });
    } catch (error) {
      setEnabled(!next);
      const message = error instanceof ApiError ? error.message : "설정을 바꾸지 못했습니다.";
      showToast({ tone: "danger", title: message });
    } finally {
      setSaving(false);
    }
  };

  return (
    <MyPageSection title="알림">
      <KeyValueRow
        label="카카오 알림톡"
        value={
          <span className="text-fg-muted">
            {enabled ? "켜짐 · 방 소식과 확인 요청" : "꺼짐 · 알림톡을 보내지 않습니다"}
          </span>
        }
        action={
          <Button variant="outline" size="sm" onClick={toggle} loading={saving}>
            {enabled ? "끄기" : "켜기"}
          </Button>
        }
      />
    </MyPageSection>
  );
}
