import Link from "next/link";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";

/** 없는 방이거나, 요약 탭이 없는 방(상담·개발)입니다. 둘을 구분해 알려주지 않습니다. */
export default function RoomSummaryNotFound() {
  return (
    <Container className="py-16">
      <EmptyState
        title="방을 찾을 수 없습니다"
        description="주소가 바뀌었거나 접근할 수 없는 방입니다."
        action={
          <Link
            href="/"
            className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            처음으로
          </Link>
        }
      />
    </Container>
  );
}
