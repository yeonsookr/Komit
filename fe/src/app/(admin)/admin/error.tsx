"use client";

import { Button } from "@/components/buttons";
import { Callout } from "@/components/feedback";

/**
 * 운영 콘솔의 오류 화면.
 *
 * 무엇이 잘못됐는지는 적지 않습니다 — 내부 주소나 스택이 화면으로 새지 않게 하려는
 * 것이고, 사유별 안내는 그 사유를 아는 화면이 직접 합니다(예: 대사 조회 실패).
 */
export default function AdminError({ reset }: Readonly<{ error: Error & { digest?: string }; reset: () => void }>) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-content-wide items-center px-4 py-10 sm:px-6">
      <Callout tone="danger" title="운영 콘솔 정보를 불러오지 못했습니다." className="w-full">
        <p>잠시 후 다시 시도해 주세요.</p>
        <Button variant="outline" size="sm" className="mt-4" onClick={reset}>
          다시 시도
        </Button>
      </Callout>
    </main>
  );
}
