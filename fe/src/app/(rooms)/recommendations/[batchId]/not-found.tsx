import Link from "next/link";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";

/** 없거나 내 것이 아닌 추천 회차. 둘을 구분해 알려주지 않습니다. */
export default function RecommendationNotFound() {
  return (
    <Container className="py-16">
      <EmptyState
        title="추천 결과를 찾을 수 없습니다"
        description="주소가 바뀌었거나 접근할 수 없는 추천입니다."
        action={
          <Link
            href="/developers"
            className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            개발자 직접 찾기
          </Link>
        }
      />
    </Container>
  );
}
