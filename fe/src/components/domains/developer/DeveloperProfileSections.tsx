import Link from "next/link";
import { Avatar, Badge, Card, Eyebrow, Money, Mono, RatingDots, Tag } from "@/components/common";
import {
  careerStatusLabel,
  formatActiveHours,
  formatCareerPeriod,
  formatFieldAndYears,
  formatPeriodDays,
} from "@/lib/developerProfile";
import { ROUTES } from "@/lib/routes";
import type { Career, DeveloperDetail, PortfolioItem, PriceCatalogItem } from "@/types/developer";

/**
 * ProfileHeader — 프로필 머리.
 * 아바타 · 이름 · 분야와 연차 · 활동 시간대 · 한 줄 소개 · 평점 · 기술 칩.
 */
export function ProfileHeader({ developer }: Readonly<{ developer: DeveloperDetail }>) {
  const activeHours = formatActiveHours(developer.activeFrom, developer.activeTo);

  return (
    <header>
      <div className="flex items-start gap-4">
        <Avatar
          size="xl"
          name={developer.name}
          src={developer.profileImageUrl ?? undefined}
          className="shrink-0"
        />

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-[28px] leading-tight font-bold text-fg">{developer.name}</h1>
            {developer.isCareerVerified && (
              <Badge tone="success" plain dot>
                증빙 확인
              </Badge>
            )}
          </div>

          <p className="mt-1 text-sm text-fg-muted">
            {formatFieldAndYears(developer.field, developer.yearsOfExperience)}
          </p>
          {/* 활동 시간대는 정하지 않았으면(null) 줄을 그리지 않습니다. */}
          {activeHours && (
            <p className="mt-0.5 text-sm text-fg-muted">
              활동 시간대 <Mono>{activeHours}</Mono>
            </p>
          )}
        </div>
      </div>

      <p className="mt-4 text-sm text-fg-secondary">{developer.intro}</p>

      <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-2">
        <RatingDots
          label="실력"
          value={developer.devRating.score ?? 0}
          score={developer.devRating.score?.toFixed(1) ?? "-"}
          count={developer.devRating.reviewCount}
          className="w-auto"
        />
        <RatingDots
          label="설명력"
          tone="primary"
          value={developer.explanatoryRating.score ?? 0}
          score={developer.explanatoryRating.score?.toFixed(1) ?? "-"}
          count={developer.explanatoryRating.reviewCount}
          className="w-auto"
        />
      </div>

      {developer.techCategories.length > 0 && (
        <ul className="mt-3 flex flex-wrap gap-1.5">
          {developer.techCategories.map((tech) => (
            <li key={tech.codeId}>
              <Tag>{tech.codeName}</Tag>
            </li>
          ))}
        </ul>
      )}
    </header>
  );
}

/** ProfileSection — 제목 + 내용. 프로필의 각 칸을 같은 간격으로 맞춥니다. */
export function ProfileSection({
  title,
  action,
  children,
}: Readonly<{ title: string; action?: React.ReactNode; children: React.ReactNode }>) {
  return (
    <section className="mt-10">
      <div className="flex items-baseline justify-between gap-4">
        <h2 className="text-xl font-bold text-fg">{title}</h2>
        {action}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  );
}

/** CareerList — 회사 · 역할 · 기간 · 확인 상태. */
export function CareerList({ careers }: Readonly<{ careers: Career[] }>) {
  if (careers.length === 0) {
    return <p className="text-sm text-fg-muted">등록한 경력이 없습니다.</p>;
  }

  return (
    <ul className="flex flex-col">
      {careers.map((career) => (
        <li
          key={career.careerId}
          className="flex flex-wrap items-baseline gap-x-4 gap-y-1 py-3 [&:not(:last-child)]:border-b [&:not(:last-child)]:border-line-subtle"
        >
          <span className="text-sm font-semibold text-fg">{career.company}</span>
          <span className="min-w-0 flex-1 text-[13px] text-fg-muted">{career.role}</span>
          <Mono className="text-[13px] text-fg-subtle">
            {formatCareerPeriod(career.startDate, career.endDate)}
          </Mono>
          <Badge tone={career.isVerified ? "success" : "warning"} plain dot size="sm">
            {careerStatusLabel(career.isVerified)}
          </Badge>
        </li>
      ))}
    </ul>
  );
}

/** PortfolioList — 작업물. 링크가 없으면 비공개로 적습니다. */
export function PortfolioList({ items }: Readonly<{ items: PortfolioItem[] }>) {
  if (items.length === 0) {
    return <p className="text-sm text-fg-muted">공개한 작업물이 없습니다.</p>;
  }

  return (
    <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {items.map((item) => (
        <li key={item.portfolioItemId}>
          <Card variant="outline" padding="none" className="h-full overflow-hidden">
            {/* 썸네일. 서버가 준 임시 주소를 그대로 겁니다. 사진이 없으면 비율만 잡은 빈 칸입니다. */}
            {item.thumbnailUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={item.thumbnailUrl}
                alt={`${item.title} 썸네일`}
                className="aspect-[16/10] w-full object-cover"
              />
            ) : (
              <div className="aspect-[16/10] w-full bg-surface" aria-hidden />
            )}
            <div className="px-3 py-3">
              <p className="truncate text-sm font-semibold text-fg">{item.title}</p>
              {item.description && (
                <p className="mt-1 line-clamp-2 text-[13px] text-fg-muted">{item.description}</p>
              )}
              {item.linkUrl ? (
                <a
                  href={item.linkUrl}
                  target="_blank"
                  rel="noreferrer noopener"
                  className="mt-1 block truncate rounded-sm text-[13px] text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  {item.linkUrl.replace(/^https?:\/\//, "")}
                </a>
              ) : (
                <p className="mt-1 text-[13px] text-fg-subtle">비공개</p>
              )}
            </div>
          </Card>
        </li>
      ))}
    </ul>
  );
}

/**
 * PriceTable — 가격표 한 덩어리.
 * 컨설팅은 세션, 개발은 작업 단위이고 개발만 걸리는 날수가 붙습니다.
 */
export function PriceTable({
  caption,
  items,
}: Readonly<{ caption: string; items: PriceCatalogItem[] }>) {
  if (items.length === 0) {
    return <p className="text-sm text-fg-muted">등록한 요금이 없습니다.</p>;
  }

  return (
    <Card variant="outline" padding="md">
      <Eyebrow>{caption}</Eyebrow>
      <ul className="mt-2 flex flex-col">
        {items.map((item) => (
          <li
            key={item.priceId}
            className="flex items-center gap-4 py-3 [&:not(:last-child)]:border-b [&:not(:last-child)]:border-line-subtle"
          >
            <span className="min-w-0 flex-1 text-sm text-fg">{item.taskName}</span>
            <Money value={item.price} />
            {formatPeriodDays(item.periodDays) && (
              <Mono className="w-10 shrink-0 text-right text-[13px] text-fg-subtle">
                {formatPeriodDays(item.periodDays)}
              </Mono>
            )}
          </li>
        ))}
      </ul>
    </Card>
  );
}

/**
 * FreeInquiryRow — "상담 — 무료" 한 줄.
 *
 * 무료 상담은 가격표에 담기지 않는 고정 정책이라 화면이 스스로 그립니다.
 * `priceCatalog` 에서 상담 줄을 찾지 않습니다(목 명세).
 */
export function FreeInquiryRow() {
  return (
    <Card variant="surface" padding="none">
      <div className="flex items-center gap-4 px-4 py-3.5">
        <span className="min-w-0 flex-1 text-sm font-semibold text-fg">상담</span>
        <span className="text-sm font-semibold text-primary">무료</span>
        <span className="text-[13px] text-fg-subtle">채팅</span>
      </div>
    </Card>
  );
}

/** 목록으로 돌아가는 링크. 프로필 머리 위에 놓입니다. */
export function BackToSearch() {
  return (
    <Link
      href={ROUTES.developers}
      className="inline-flex rounded-sm text-[13px] text-fg-subtle hover:text-fg hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      ‹ 개발자 찾기
    </Link>
  );
}
