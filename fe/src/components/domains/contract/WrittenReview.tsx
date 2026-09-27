import Link from "next/link";
import { ReviewList } from "@/components/domains/developer";
import { formatDateTimeLabel } from "@/lib/format";
import { ROUTES } from "@/lib/routes";
import type { Review } from "@/types/developer";

export interface WrittenReviewProps {
  review: Review;
  /** 개발자가 열었는지. 받은 리뷰로 보여 주고 답글 쓰는 곳으로 안내합니다. */
  received: boolean;
}

/** WrittenReview — 이 계약에 이미 남긴 리뷰 (`리뷰작성_남긴뒤_상태`). */
export function WrittenReview({ review, received }: Readonly<WrittenReviewProps>) {
  const at = formatDateTimeLabel(review.createdAt);

  return (
    <section className="mt-8">
      <header>
        <h2 className="text-xl font-bold text-fg">{received ? "받은 리뷰" : "리뷰를 남겼습니다"}</h2>
        <p className="mt-1 text-[13px] text-fg-subtle">
          {received ? `${at}에 의뢰인이 남겼습니다` : `${at} · 남긴 리뷰는 개발자 프로필에 보입니다`}
        </p>
      </header>

      <div className="mt-4">
        <ReviewList reviews={[review]} />
      </div>

      {received && (
        <Link href={ROUTES.mypageProfile} className="text-[13px] font-semibold text-primary">
          {review.reply ? "내 프로필에서 답글 고치기 ›" : "내 프로필에서 답글 쓰기 ›"}
        </Link>
      )}
    </section>
  );
}
