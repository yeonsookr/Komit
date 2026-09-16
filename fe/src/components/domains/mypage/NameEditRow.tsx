"use client";

import * as React from "react";
import { Button } from "@/components/buttons";
import { Input, KeyValueRow } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { useAuthStore } from "@/stores/auth";
import { ApiError } from "@/types/api";
import type { Me } from "@/types/user";

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
 * 저장하면 헤더의 이름도 함께 바꿔야 합니다. 헤더는 인증 스토어를 읽으므로 응답으로
 * 받은 내 정보를 스토어에 넣습니다. 이걸 빠뜨리면 마이페이지에는 새 이름이,
 * 헤더에는 옛 이름이 남습니다.
 */
export function NameEditRow({ name }: Readonly<NameEditRowProps>) {
  const showToast = useToast();
  const setUser = useAuthStore((s) => s.setUser);

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
      const { data } = await clientApi.patch<Me>(INTERNAL.MY_NAME, { name: trimmed });

      // 헤더가 읽는 자리를 함께 갱신합니다.
      setUser(data);
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
