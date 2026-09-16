import Link from "next/link";
import { Container } from "@/components/layouts";
import { EmptyState } from "@/components/feedback";
import { ROUTES } from "@/lib/routes";

/** 없는 방이거나 내가 들어가 있지 않은 방. 둘을 구분해 알려주지 않습니다. */
export default function RoomNotFound() {
  return (
    <Container className="py-16">
      <EmptyState
        title="방을 찾을 수 없습니다"
        description="주소가 바뀌었거나 접근할 수 없는 방입니다."
        action={
          <Link
            href={ROUTES.home}
            className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            처음으로
          </Link>
        }
      />
    </Container>
  );
}
