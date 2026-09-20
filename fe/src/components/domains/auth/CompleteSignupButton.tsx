"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/buttons";
import { useToast } from "@/components/feedback";
import { finishSignup } from "@/lib/api/services/signup";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

export function CompleteSignupButton({ next }: Readonly<{ next?: string }>) {
  const router = useRouter();
  const showToast = useToast();
  const [saving, setSaving] = useState(false);

  const complete = async () => {
    if (saving) return;
    setSaving(true);
    try {
      await finishSignup();
      router.replace(ROUTES.signupComplete(next));
      router.refresh();
    } catch (error) {
      showToast({
        tone: "danger",
        title: error instanceof ApiError ? error.message : "가입을 완료하지 못했습니다. 다시 시도해 주세요.",
      });
      setSaving(false);
    }
  };

  return (
    <Button type="button" variant="outline" size="lg" fullWidth loading={saving} onClick={complete}>
      다음에 하기
    </Button>
  );
}
