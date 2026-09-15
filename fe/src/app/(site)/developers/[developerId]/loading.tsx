import { Container } from "@/components/layouts";

/**
 * 프로필을 읽는 동안. 실제 화면과 같은 자리에 회색 블록을 둡니다.
 *
 * ⚠️ 상태 코드 절충 — 로딩 셸이 먼저 흘러나가므로 뒤이어 notFound() 가 불려도
 * HTTP 상태는 200 으로 굳습니다(화면은 not-found 가 그려집니다). 공개 프로필은
 * 색인 대상이라 없는 개발자에 404 를 주는 편이 옳지만, 그러려면 이 파일과
 * 상위 `developers/loading.tsx` 를 함께 지워 목록 화면의 스켈레톤까지 잃습니다.
 * 색인 문제가 실제로 생기면 두 파일을 함께 걷어내는 쪽으로 정합니다.
 */
export default function DeveloperProfileLoading() {
  return (
    <Container className="py-8 sm:py-10" aria-busy>
      <span className="sr-only">개발자 프로필을 불러오는 중입니다.</span>

      <div className="h-4 w-24 animate-pulse rounded-sm bg-surface" />

      <div className="mt-4 flex items-start gap-4">
        <div className="size-18 shrink-0 animate-pulse rounded-full bg-surface" />
        <div className="min-w-0 flex-1">
          <div className="h-8 w-32 animate-pulse rounded-md bg-surface" />
          <div className="mt-2 h-4 w-40 animate-pulse rounded-sm bg-surface" />
          <div className="mt-2 h-4 w-36 animate-pulse rounded-sm bg-surface" />
        </div>
      </div>

      <div className="mt-4 h-4 w-full max-w-md animate-pulse rounded-sm bg-surface" />

      {[0, 1, 2].map((section) => (
        <div key={section} className="mt-10">
          <div className="h-7 w-24 animate-pulse rounded-md bg-surface" />
          <div className="mt-4 h-32 animate-pulse rounded-xl bg-surface" />
        </div>
      ))}
    </Container>
  );
}
