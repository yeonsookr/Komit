"use client";

import { Button } from "@/components/buttons";
import { Callout } from "@/components/feedback";
import { Container } from "@/components/layouts";

/**
 * 마이페이지를 읽지 못했을 때. 원인은 서버 로그에만 남깁니다.
 * 로그인하지 않은 경우도 여기로 옵니다 — 로그인 화면으로 돌려보내는 처리는
 * 인증 연동 단계에서 다른 보호 화면과 함께 넣습니다.
 */
export default function MyPageError({ reset }: Readonly<{ error: Error; reset: () => void }>) {
  return (
    <Container className="py-16">
      <Callout
        tone="danger"
        title="마이페이지를 불러오지 못했습니다"
        action={<Button onClick={reset}>다시 시도</Button>}
      >
        로그인이 풀렸거나 잠시 문제가 생겼을 수 있습니다. 다시 시도해 주세요.
      </Callout>
    </Container>
  );
}
