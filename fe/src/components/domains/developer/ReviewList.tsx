import { Avatar, Card, RatingDots } from "@/components/common";
import { EmptyState } from "@/components/feedback";
import { formatRelativeTime } from "@/lib/format";
import type { Rating, Review } from "@/types/developer";

/** 계약 종류 표기. */
const CONTRACT_TYPE_LABEL = {
  CONSULTING: "컨설팅",
  DEVELOPMENT: "개발",
} as const;

export interface ReviewSummaryProps {
  devRating: Rating;
  explanatoryRating: Rating;
}

/** ReviewSummary — 리뷰 칸 머리의 두 평균 점수. */
export function ReviewSummary({ devRating, explanatoryRating }: Readonly<ReviewSummaryProps>) {
  return (
    <Card variant="surface" padding="md">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <ScoreBlock label="실력" rating={devRating} />
        <ScoreBlock label="설명력" rating={explanatoryRating} tone="primary" />
      </div>
    </Card>
  );
}

function ScoreBlock({
  label,
  rating,
  tone = "ink",
}: Readonly<{ label: string; rating: Rating; tone?: "ink" | "primary" }>) {
  return (
    <div>
      <p className="flex items-baseline gap-2">
        <span className={tone === "primary" ? "text-sm text-primary" : "text-sm text-fg-subtle"}>
          {label}
        </span>
        <span
          className={
            tone === "primary"
              ? "font-mono text-2xl font-bold text-primary tabular-nums"
              : "font-mono text-2xl font-bold text-fg tabular-nums"
          }
        >
          {rating.score?.toFixed(1) ?? "-"}
        </span>
      </p>
      <RatingDots
        value={rating.score ?? 0}
        score=""
        count={rating.reviewCount}
        tone={tone}
        className="mt-1 w-auto"
      />
    </div>
  );
}

export interface ReviewListProps {
  reviews: Review[];
  /** 지금 화면에 보여줄 수. 프로필은 앞의 몇 건만 보여줍니다. */
  limit?: number;
}

/**
 * ReviewList — 받은 리뷰. 최신순으로 옵니다.
 *
 * 의뢰인 이름은 서버가 이미 가려서(`김O미`) 내려주므로 화면이 따로 가리지 않습니다.
 * 어느 프로젝트의 리뷰인지는 담기지 않고 계약 종류만 옵니다.
 */
export function ReviewList({ reviews, limit }: Readonly<ReviewListProps>) {
  if (reviews.length === 0) {
    return (
      <EmptyState
        title="아직 받은 리뷰가 없습니다"
        description="계약이 끝나면 의뢰인이 리뷰를 남길 수 있습니다."
      />
    );
  }

  const shown = limit === undefined ? reviews : reviews.slice(0, limit);

  return (
    <ul className="flex flex-col">
      {shown.map((review) => (
        <li
          key={review.reviewId}
          className="flex gap-3 py-5 [&:not(:last-child)]:border-b [&:not(:last-child)]:border-line-subtle"
        >
          <Avatar size="sm" name={review.clientName} className="mt-0.5 shrink-0" />

          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <span className="text-sm font-semibold text-fg">{review.clientName}</span>
              <span className="text-[13px] text-fg-subtle">
                실력 <span className="font-mono tabular-nums">{review.devScore.toFixed(1)}</span>
              </span>
              <span className="text-[13px] text-primary">
                설명력 <span className="font-mono tabular-nums">{review.expScore.toFixed(1)}</span>
              </span>
              <span className="ml-auto shrink-0 text-[13px] text-fg-subtle">
                {CONTRACT_TYPE_LABEL[review.contractType]} ·{" "}
                <span className="font-mono tabular-nums">
                  {formatRelativeTime(review.createdAt)}
                </span>
              </span>
            </div>

            {/* 점수만 주고 글은 안 남길 수 있습니다. */}
            {review.content && (
              <p className="mt-2 text-sm leading-6 text-fg-secondary">{review.content}</p>
            )}

            {review.reply && (
              <div className="mt-3 rounded-lg bg-surface px-3 py-2.5">
                <p className="text-xs font-semibold text-fg-subtle">개발자 답글</p>
                <p className="mt-1 text-[13px] leading-5 text-fg-secondary">{review.reply}</p>
              </div>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}
