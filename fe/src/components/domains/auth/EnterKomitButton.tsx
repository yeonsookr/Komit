"use client";

import { useRouter } from "next/navigation";
import * as React from "react";
import { Button } from "@/components/buttons";
import { useToast } from "@/components/feedback";
import { useAuthStore } from "@/stores/auth";

export interface EnterKomitButtonProps {
  href: string;
}

/** 가입 완료 후 세션을 다시 읽고, 로그인 상태가 확인된 경우에만 서비스로 이동합니다. */
export function EnterKomitButton({ href }: Readonly<EnterKomitButtonProps>) {
  const router = useRouter();
  const showToast = useToast();
  const loadMe = useAuthStore((state) => state.loadMe);
  const [loading, setLoading] = React.useState(false);

  const enter = async () => {
    if (loading) return;

    setLoading(true);
    const authenticated = await loadMe();
    if (!authenticated) {
      showToast({
        tone: "danger",
        title: "로그인 상태를 확인하지 못했습니다. 다시 시도해 주세요.",
      });
      setLoading(false);
      return;
    }

    router.replace(href);
    router.refresh();
  };

  return (
    <Button size="lg" fullWidth loading={loading} className="mt-9" onClick={enter}>
      Komit 시작하기
    </Button>
  );
}
