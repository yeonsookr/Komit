import { Container } from "@/components/layouts";

/**
 * 방을 읽는 동안. 실제 화면과 같은 자리에 회색 블록을 둡니다.
 *
 * 참고 — 이 파일이 있으면 Next 가 셸을 먼저 흘려보내므로, 뒤이어 notFound() 가
 * 불려도 HTTP 상태는 이미 200 으로 굳습니다(화면은 not-found 가 그려집니다).
 * 방 화면은 로그인이 필요해 색인 대상이 아니라 그대로 둡니다. 상태 코드가
 * 404 여야 하는 화면에서는 이 파일을 두지 않습니다.
 */
export default function RoomLoading() {
  return (
    <Container className="py-8 sm:py-10" aria-busy>
      <span className="sr-only">대화를 불러오는 중입니다.</span>
      <div className="h-5 w-32 animate-pulse rounded-sm bg-surface" />
      <div className="mt-3 h-9 w-64 animate-pulse rounded-md bg-surface" />
      <div className="mt-6 h-14 animate-pulse rounded-xl bg-surface" />
      <div className="mt-10 h-7 w-20 animate-pulse rounded-md bg-surface" />
      <div className="mt-4 h-80 animate-pulse rounded-xl bg-surface" />
    </Container>
  );
}
