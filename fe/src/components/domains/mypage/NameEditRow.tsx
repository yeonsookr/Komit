"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Input, KeyValueRow } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { useAuthStore } from "@/stores/auth";
import { ApiError } from "@/types/api";

/** 목 명세가 정한 길이. 이보다 길면 서버가 422 를 줍니다. */
const MAX_NAME_LENGTH = 20;

export interface NameEditRowProps {
  /** 서버에서 읽어 온 지금 이름. */
  name: string;
}

/**
 * NameEditRow — 마이페이지 계정 칸의 이름 줄.
 *
 * 평소에는 이름과 「이름 바꾸기」만 보이고, 누르면 그 자리에서 입력칸으로 바뀝니다.
 * 별도 화면으로 넘기지 않는 것은 바꿀 값이 하나뿐이라서입니다.
 *
 * 저장하면 헤더의 이름도 함께 바꿔야 합니다. 헤더는 인증 스토어를 읽는데, 이름
 * 바꾸기 응답에는 알림 개수 같은 헤더에 필요한 값이 없습니다. 그래서 응답을 스토어에
 * 넣지 않고 내 정보를 다시 읽습니다 — 응답을 그대로 넣으면 이름은 바뀌지만 알림
 * 배지가 사라집니다.
 */
export function NameEditRow({ name }: Readonly<NameEditRowProps>) {
  const router = useRouter();
  const showToast = useToast();
  const loadMe = useAuthStore((s) => s.loadMe);

  const [editing, setEditing] = React.useState(false);
  const [draft, setDraft] = React.useState(name);
  const [saving, setSaving] = React.useState(false);

  // 서버에서 다시 읽어 오면(저장 후 새로고침) 그 값으로 맞춥니다.
  React.useEffect(() => setDraft(name), [name]);

  const trimmed = draft.trim();
  const canSave = trimmed.length > 0 && trimmed !== name && !saving;

  const open = () => {
    setDraft(name);
    setEditing(true);
  };

  const save = async () => {
    if (!canSave) return;
    setSaving(true);

    try {
      await clientApi.patch(INTERNAL.MY_NAME, { name: trimmed });

      // 헤더가 읽는 자리를 다시 읽어 맞춥니다.
      await loadMe();
      // 이 줄이 보여주는 이름은 서버 컴포넌트가 내려준 값이라 다시 그려야 바뀝니다.
      router.refresh();
      showToast({ tone: "success", title: "이름을 바꿨습니다." });
      setEditing(false);
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "이름을 바꾸지 못했습니다.";
      showToast({ tone: "danger", title: message });
    } finally {
      setSaving(false);
    }
  };

  if (!editing) {
    return (
      <KeyValueRow
        label="이름"
        value={name}
        action={
          <Button variant="outline" size="sm" onClick={open}>
            이름 바꾸기
          </Button>
        }
      />
    );
  }

  return (
    <KeyValueRow
      label="이름"
      value={
        <Input
          autoFocus
          size="sm"
          value={draft}
          maxLength={MAX_NAME_LENGTH}
          aria-label="새 이름"
          disabled={saving}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.nativeEvent.isComposing) save();
            if (event.key === "Escape") setEditing(false);
          }}
          containerClassName="max-w-[240px]"
          className="max-w-[240px]"
        />
      }
      action={
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => setEditing(false)} disabled={saving}>
            취소
          </Button>
          <Button size="sm" onClick={save} loading={saving} disabled={!canSave}>
            저장
          </Button>
        </div>
      }
    />
  );
}
