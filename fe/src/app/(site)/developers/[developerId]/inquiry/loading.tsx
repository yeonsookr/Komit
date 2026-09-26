import { Container } from "@/components/layouts";

/** 내 요청서를 읽는 동안. 프로필의 로딩 화면이 대신 뜨지 않도록 이 화면 모양으로 둡니다. */
export default function DeveloperInquiryLoading() {
  return (
    <Container className="py-8 sm:py-10" aria-busy>
      <span className="sr-only">요청서를 불러오는 중입니다.</span>

      <div className="h-8 w-72 animate-pulse rounded-sm bg-surface" />
      <div className="mt-3 h-4 w-96 max-w-full animate-pulse rounded-sm bg-surface" />

      <div className="mt-8 h-4 w-12 animate-pulse rounded-sm bg-surface" />
      <div className="mt-3 flex flex-col gap-2">
        <div className="h-16 animate-pulse rounded-md bg-surface" />
        <div className="h-16 animate-pulse rounded-md bg-surface" />
      </div>
    </Container>
  );
}
