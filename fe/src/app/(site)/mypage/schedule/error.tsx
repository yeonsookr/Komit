"use client";

import { Button } from "@/components/buttons";
import { Callout } from "@/components/feedback";
import { Container } from "@/components/layouts";

export default function MyScheduleError({
  reset,
}: Readonly<{ error: Error; reset: () => void }>) {
  return (
    <Container className="py-16">
      <Callout
        tone="danger"
        title="일정을 불러오지 못했습니다"
        action={<Button onClick={reset}>다시 시도</Button>}
      >
        잠시 후 다시 시도해 주세요. 문제가 이어지면 문의해 주세요.
      </Callout>
    </Container>
  );
}
