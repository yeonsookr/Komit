"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/buttons";
import { useToast } from "@/components/feedback";
import { clientApi } from "@/lib/api/client";
import { INTERNAL } from "@/lib/api/endpoints";
import { ROUTES } from "@/lib/routes";
import { useAuthStore } from "@/stores/auth";
import { ApiError } from "@/types/api";

export interface WithdrawButtonProps {
  /** 서버가 판정한 값. 거짓이면 누를 수 없습니다. */
  canWithdraw: boolean;
}

/**
 * WithdrawButton — 「탈퇴하기」.
 *
 * 잠금은 두 겹입니다. 화면이 여기서 막고, 서버가 같은 조건을 다시 봅니다. 화면만
 * 믿으면 탭을 열어 둔 사이에 계약이 생겨도 그대로 통과합니다 — 그때는 409 가 오고
 * 그 문장을 그대로 보여줍니다.
 *
 * 성공하면 쿠키는 Route Handler 가 지웠으므로 헤더가 읽는 인증 스토어도 비우고
 * 첫 화면으로 보냅니다. 스토어를 비우지 않으면 로그아웃됐는데 헤더에는 이름이
 * 남습니다.
 */
export function WithdrawButton({ canWithdraw }: Readonly<WithdrawButtonProps>) {
  const router = useRouter();
  const showToast = useToast();
  const clearAuth = useAuthStore((s) => s.clear);
  const [leaving, setLeaving] = React.useState(false);

  const submit = async () => {
    if (!canWithdraw || leaving) return;
    setLeaving(true);

    try {
      await clientApi.post(INTERNAL.WITHDRAWAL);

      clearAuth();
      router.replace(ROUTES.home);
      // 서버 컴포넌트를 쿠키가 없는 상태로 다시 그리게 합니다.
      router.refresh();
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "탈퇴하지 못했습니다.";
      showToast({ tone: "danger", title: message });
      setLeaving(false);
    }
  };

  return (
    <>
      {!canWithdraw && (
        <p className="mt-10 text-[13px] text-fg-subtle">
          남은 의뢰와 예치금을 정리하면 이 버튼이 열립니다.
        </p>
      )}
      <Button
        variant="danger"
        size="xl"
        fullWidth
        className={canWithdraw ? "mt-10" : "mt-3"}
        disabled={!canWithdraw}
        loading={leaving}
        onClick={submit}
      >
        탈퇴하기
      </Button>
    </>
  );
}
