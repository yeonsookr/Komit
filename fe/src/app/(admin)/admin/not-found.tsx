import Link from "next/link";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { ROUTES } from "@/lib/routes";

/**
 * 운영 콘솔 안에서 없는 것을 찾았을 때. 없는 계약 양식 판이 여기로 옵니다.
 *
 * **사용자 화면으로 돌려보내지 않습니다.** 운영자는 사용자 계정이 없을 수 있어
 * 랜딩으로 보내면 갈 곳이 없습니다. 콘솔 홈으로 보냅니다.
 */
export default function AdminNotFound() {
  return (
    <Container width="wide" className="py-16">
      <EmptyState
        title="찾을 수 없습니다"
        description="주소가 바뀌었거나 지워진 항목입니다."
        action={
          <Link
            href={ROUTES.adminHome}
            className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            운영 콘솔 홈으로
          </Link>
        }
      />
    </Container>
  );
}
