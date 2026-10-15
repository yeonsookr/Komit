import Link from "next/link";
import { EmptyState } from "@/components/feedback";
import { Container } from "@/components/layouts";
import { ROUTES } from "@/lib/routes";

/** 없는 세션이거나 내가 당사자가 아닌 세션. 둘을 구분해 알려주지 않습니다. */
export default function SessionNotFound() {
  return (
    <Container className="py-16">
      <EmptyState
        title="세션을 찾을 수 없습니다"
        description="주소가 바뀌었거나 들어갈 수 없는 세션입니다."
        action={
          <Link
            href={ROUTES.requests}
            className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            의뢰 목록으로
          </Link>
        }
      />
    </Container>
  );
}
