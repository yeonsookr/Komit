import { Container } from "@/components/layouts";

/**
 * 개발자를 찾는 동안. 실제 화면과 같은 자리에 회색 블록을 둡니다.
 *
 * 검색 조건이 주소에 담기므로, 조건을 바꿀 때마다 이 화면을 지납니다.
 * 목록 높이가 크게 출렁이지 않도록 카드 자리를 세 칸 잡아 둡니다.
 */
export default function DevelopersLoading() {
  return (
    <Container className="py-8 sm:py-10" aria-busy>
      <span className="sr-only">개발자를 찾는 중입니다.</span>

      <div className="h-9 w-40 animate-pulse rounded-md bg-surface" />
      <div className="mt-6 h-12 animate-pulse rounded-lg bg-surface" />
      <div className="mt-4 h-8 w-72 animate-pulse rounded-full bg-surface" />
      <div className="mt-4 h-8 w-28 animate-pulse rounded-full bg-surface" />

      <div className="mt-8 flex items-center justify-between">
        <div className="h-4 w-24 animate-pulse rounded-sm bg-surface" />
        <div className="h-4 w-28 animate-pulse rounded-sm bg-surface" />
      </div>

      <ul className="mt-6 flex flex-col gap-6">
        {[0, 1, 2].map((row) => (
          <li key={row} className="flex gap-4">
            <div className="size-11 shrink-0 animate-pulse rounded-full bg-surface" />
            <div className="min-w-0 flex-1">
              <div className="h-5 w-28 animate-pulse rounded-sm bg-surface" />
              <div className="mt-2 h-4 w-40 animate-pulse rounded-sm bg-surface" />
              <div className="mt-3 h-4 w-full max-w-md animate-pulse rounded-sm bg-surface" />
              <div className="mt-3 h-4 w-64 animate-pulse rounded-sm bg-surface" />
            </div>
          </li>
        ))}
      </ul>
    </Container>
  );
}
