import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Container } from "@/components/layouts";
import { Button } from "@/components/buttons";
import { StickyBar } from "@/components/common";
import {
  BackToSearch,
  CareerList,
  FreeInquiryRow,
  PortfolioList,
  PriceTable,
  ProfileHeader,
  ProfileSection,
  ReviewList,
  ReviewSummary,
} from "@/components/domains/developer";
import { getDeveloper, getDeveloperReviews } from "@/lib/api/services";
import { splitPriceCatalog } from "@/lib/developerProfile";
import { ApiError } from "@/types/api";

/** 프로필에 미리 보여주는 리뷰 수. 나머지는 '모두 보기'로 넘깁니다. */
const PREVIEW_REVIEW_COUNT = 3;

/** 경로의 개발자 식별자를 읽습니다. 숫자가 아니면 없는 개발자로 봅니다. */
function parseDeveloperId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const id = Number(value);
  return id > 0 ? id : null;
}

/** 프로필을 읽습니다. 없거나 탈퇴했으면 404 화면으로 보냅니다. */
async function loadDeveloper(developerId: number) {
  try {
    return await getDeveloper(developerId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ developerId: string }>;
}): Promise<Metadata> {
  const { developerId } = await params;
  const id = parseDeveloperId(developerId);
  if (id === null) return { title: "개발자를 찾을 수 없습니다 | Komit" };

  const developer = await loadDeveloper(id);

  return {
    title: `${developer.name} 개발자 | Komit`,
    description: developer.intro,
    openGraph: {
      title: `${developer.name} 개발자 | Komit`,
      description: developer.intro,
    },
  };
}

export default async function DeveloperProfilePage({
  params,
}: {
  params: Promise<{ developerId: string }>;
}) {
  const { developerId } = await params;
  const id = parseDeveloperId(developerId);
  if (id === null) notFound();

  const developer = await loadDeveloper(id);
  const reviews = await getDeveloperReviews(id, { size: PREVIEW_REVIEW_COUNT });

  const prices = splitPriceCatalog(developer.priceCatalog);
  // 리뷰 총 건수는 devRating.reviewCount 를 씁니다(목 명세).
  const reviewCount = developer.devRating.reviewCount;

  return (
    <Container className="py-8 sm:py-10">
      <BackToSearch />

      <div className="mt-4">
        <ProfileHeader developer={developer} />
      </div>

      <ProfileSection title="소개">
        <p className="text-sm leading-7 whitespace-pre-wrap text-fg-secondary">
          {developer.description}
        </p>
      </ProfileSection>

      <ProfileSection title="경력">
        <CareerList careers={developer.careers} />
      </ProfileSection>

      <ProfileSection title="포트폴리오">
        <PortfolioList items={developer.portfolioItems} />
      </ProfileSection>

      <ProfileSection title="상담">
        <FreeInquiryRow />
      </ProfileSection>

      <ProfileSection title="컨설팅">
        <PriceTable caption="세션" items={prices.CONSULT} />
      </ProfileSection>

      <ProfileSection title="개발 · 작업별 요금">
        <PriceTable caption="작업" items={prices.DEVELOP} />
      </ProfileSection>

      <ProfileSection
        title="리뷰"
        action={
          reviewCount > PREVIEW_REVIEW_COUNT ? (
            <span className="text-[13px] text-fg-subtle">
              <span className="font-mono tabular-nums">{reviewCount}</span>개 중 앞의{" "}
              <span className="font-mono tabular-nums">{PREVIEW_REVIEW_COUNT}</span>개
            </span>
          ) : undefined
        }
      >
        <ReviewSummary
          devRating={developer.devRating}
          explanatoryRating={developer.explanatoryRating}
        />
        <div className="mt-2">
          <ReviewList reviews={reviews.content} />
        </div>
      </ProfileSection>

      {/* 하단 고정 CTA. 무료 상담은 요청서를 고른 뒤 열리므로 의뢰 화면으로 보냅니다. */}
      <StickyBar className="mt-10">
        <Button variant="primary" size="lg" fullWidth>
          무료로 물어보기
        </Button>
      </StickyBar>
    </Container>
  );
}
