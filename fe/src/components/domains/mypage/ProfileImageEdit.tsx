"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { Avatar, KeyValueRow } from "@/components/common";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { uploadFromBrowser } from "@/lib/upload/browserUpload";
import { INTERNAL } from "@/lib/api/endpoints";
import { useAuthStore } from "@/stores/auth";
import { ApiError } from "@/types/api";
import type { MyAccount } from "@/types/mypage";

export interface ProfileImageEditProps {
  name: string;
  profileImageUrl: string | null;
}

/** ProfileImageEdit — 사진을 고르면 바로 올리고 마이페이지와 헤더를 함께 갱신합니다. */
export function ProfileImageEdit({
  name,
  profileImageUrl,
}: Readonly<ProfileImageEditProps>) {
  const router = useRouter();
  const showToast = useToast();
  const loadMe = useAuthStore((state) => state.loadMe);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const [currentUrl, setCurrentUrl] = React.useState(profileImageUrl);
  const [uploading, setUploading] = React.useState(false);

  React.useEffect(() => setCurrentUrl(profileImageUrl), [profileImageUrl]);

  const changeImage = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    if (file.size === 0 || !file.type.startsWith("image/")) {
      showToast({ tone: "danger", title: "이미지 파일을 선택해 주세요." });
      return;
    }

    setUploading(true);

    try {
      const uploaded = await uploadFromBrowser(file, "PROFILE_IMAGE");
      const { data } = await clientApi.post<MyAccount>(INTERNAL.PROFILE_IMAGE, {
        fileId: uploaded.fileId,
      });
      setCurrentUrl(data.profileImageUrl);
      await loadMe();
      router.refresh();
      showToast({ tone: "success", title: "프로필 사진을 바꿨습니다." });
    } catch (error) {
      const message =
        error instanceof ApiError ? error.message : "프로필 사진을 바꾸지 못했습니다.";
      showToast({ tone: "danger", title: message });
    } finally {
      setUploading(false);
    }
  };

  return (
    <KeyValueRow
      id="profile-image"
      className="scroll-mt-20"
      label="프로필 사진"
      value={
        <span className="flex items-center gap-3">
          <Avatar size="md" name={name} src={currentUrl ?? undefined} />
          <span className="text-[13px] text-fg-muted">
            {currentUrl
              ? "현재 사용 중인 프로필 사진입니다"
              : "등록한 사진이 없어 이름 첫 글자를 씁니다"}
          </span>
        </span>
      }
      action={
        <>
          <input
            ref={inputRef}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            aria-label="새 프로필 사진"
            className="sr-only"
            disabled={uploading}
            onChange={changeImage}
          />
          <Button
            variant="outline"
            size="sm"
            loading={uploading}
            onClick={() => inputRef.current?.click()}
          >
            사진 바꾸기
          </Button>
        </>
      }
    />
  );
}
