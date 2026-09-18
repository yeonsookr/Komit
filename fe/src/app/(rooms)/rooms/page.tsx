import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/feedback";
import { Container } from "@/components/layouts";
import { getRooms } from "@/lib/api/services";
import { requireLogin } from "@/lib/auth/guard";
import { ROUTES } from "@/lib/routes";

export const metadata: Metadata = {
  title: "의뢰 | Komit",
  description: "맡긴 의뢰와 맡은 의뢰를 한곳에서 봅니다.",
};

/**
 * 의뢰 목록. 헤더 「의뢰」가 여기로 옵니다.
 *
 * 방을 고르지 않은 상태라 본문에는 안내만 둡니다. 왼쪽 목록은 이 그룹의 레이아웃이
 * 이미 그리고 있어 여기서 다시 부르지 않습니다.
 *
 * 방 목록을 한 번 더 읽는 것은 **방이 하나도 없을 때** 다른 말을 하기 위해서입니다.
 * 목록이 비었는데 "왼쪽에서 고르세요"라고 하면 고를 것이 없습니다.
 */
export default async function RoomsPage() {
  const rooms = await requireLogin(() => getRooms());

  return (
    <Container className="py-16">
      {rooms.length === 0 ? (
        <EmptyState
          title="아직 의뢰가 없습니다"
          description="개발자에게 무료 상담을 요청하면 이곳에 방이 생깁니다."
          action={
            <Link
              href={ROUTES.developers}
              className="rounded-sm text-[13px] font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              개발자 찾기
            </Link>
          }
        />
      ) : (
        /* 좁은 화면에서는 목록이 위로 올라가므로 "왼쪽" 같은 방향을 적지 않습니다. */
        <EmptyState
          title="의뢰를 골라 주세요"
          description="목록에서 방을 고르면 나눈 이야기와 진행 상황이 보입니다."
        />
      )}
    </Container>
  );
}
