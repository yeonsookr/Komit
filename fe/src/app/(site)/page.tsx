import type { Metadata } from "next";
import { Container } from "@/components/layouts";
import {
  CheckupIntro,
  FeaturedDevelopers,
  LandingHero,
  ProcessSteps,
  SymptomGrid,
} from "@/components/domains/landing";
import { getFeaturedDevelopers, getLandingSummary } from "@/lib/api/services";
import type { FeaturedDeveloper } from "@/types/landing";

export const metadata: Metadata = {
  title: "Komit",
  description:
    "바이브 코딩으로 만든 앱이 배포·DB·도메인에서 막혔다면, 무료 상담으로 개발자를 만나고 확인 문장대로 안전하게 대금을 맡기세요.",
  openGraph: {
    title: "Komit",
    description: "출시는 스스로, 못 하면 맡기고, 맡길 땐 안전하게.",
  },
};

/**
 * 랜딩은 요청마다 렌더링합니다.
 *
 * 정적 생성으로 두면 이미지를 빌드하는 시점의 데이터(빌드 인자로 넘긴 목 서버 응답)가 HTML에 박혀 다음 갱신까지 그대로
 * 나가고, 갱신 중 서버가 잠깐 흔들리면 빈 칸이 한 시간 캐시됩니다. 추천 개발자와 상담 수는 실서버의 지금 값을 보여줘야
 * 하는 칸이라 동적 렌더링으로 둡니다. 사용자 데이터는 읽지 않으므로 헤더는 비로그인 상태로 그려집니다.
 */
export const dynamic = "force-dynamic";

/**
 * 추천 개발자를 읽지 못해도 랜딩은 떠야 합니다. 요청마다 새로 읽으므로 캐시에 남지 않고,
 * 이번 요청만 빈 목록으로 두고 원인은 서버 로그에만 남깁니다.
 */
async function loadFeaturedDevelopers(): Promise<FeaturedDeveloper[]> {
  try {
    return await getFeaturedDevelopers();
  } catch (error) {
    console.error("[landing] 추천 개발자를 읽지 못했습니다", error);
    return [];
  }
}

export default async function HomePage() {
  const [summary, developers] = await Promise.all([getLandingSummary(), loadFeaturedDevelopers()]);

  return (
    <>
      <Container width="landing">
        <LandingHero weeklyInquiryRoomCount={summary?.weeklyInquiryRoomCount ?? null} />
        <SymptomGrid />
      </Container>

      {/* 추천 개발자와 진행 순서는 회색 밴드 위에 놓입니다. */}
      <div className="mt-6 bg-surface">
        <Container width="landing">
          <FeaturedDevelopers developers={developers} />
          <ProcessSteps />
        </Container>
      </div>

      <Container width="landing">
        <CheckupIntro />
      </Container>
    </>
  );
}
