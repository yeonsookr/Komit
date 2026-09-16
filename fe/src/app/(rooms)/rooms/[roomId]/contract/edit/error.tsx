"use client";

import { Button } from "@/components/buttons";
import { Callout } from "@/components/feedback";
import { Container } from "@/components/layouts";

/** 계약서를 읽지 못했을 때. 원인은 서버 로그에 있고 화면에는 내보내지 않습니다. */
export default function RoomContractError({ reset }: { error: Error; reset: () => void }) {
  return (
    <Container className="py-16">
      <Callout
        tone="danger"
        title="계약서 화면을 불러오지 못했습니다"
        action={<Button onClick={reset}>다시 시도</Button>}
      >
        잠시 후 다시 시도해 주세요. 문제가 이어지면 문의해 주세요.
      </Callout>
    </Container>
  );
}
