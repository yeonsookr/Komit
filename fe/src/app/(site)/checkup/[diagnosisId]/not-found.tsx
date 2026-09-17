import Link from "next/link";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { ROUTES } from "@/lib/routes";

/** 없거나 내 것이 아닌 진단. 둘을 구분해 알려주지 않습니다. */
export default function CheckupNotFound() {
  return (
    <Container className="py-16">
      <EmptyState
        title="사전점검을 찾을 수 없습니다"
        description="주소가 바뀌었거나 접근할 수 없는 점검입니다."
        action={
          <Link
            href={ROUTES.checkup}
            className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            처음부터 점검하기
          </Link>
        }
      />
    </Container>
  );
}
