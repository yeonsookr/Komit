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
 * 랜딩은 정적으로 생성하고 한 시간마다 갱신합니다(컨벤션 7장).
 * 지표는 주 단위, 추천 개발자는 자주 바뀌지 않아 그 주기로 충분합니다.
 * 사용자 데이터를 읽지 않으므로 헤더는 비로그인 상태로 그려집니다.
 */
export const revalidate = 3600;

/**
 * 추천 개발자를 읽지 못해도 랜딩은 떠야 합니다. 빈 목록으로 두고 원인은 서버 로그에만 남깁니다.
 * (빌드 시점에 목 서버가 없어도 정적 생성이 실패하지 않게 하는 장치이기도 합니다.)
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
