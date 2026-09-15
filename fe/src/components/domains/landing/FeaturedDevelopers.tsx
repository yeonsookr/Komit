import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { Avatar, Card, Money, RatingDots } from "@/components/common";
import { ROUTES } from "@/lib/routes";
import type { FeaturedDeveloper } from "@/types/landing";
import type { TaskType } from "@/types/developer";

export interface FeaturedDevelopersProps {
  developers: FeaturedDeveloper[];
}

/**
 * FeaturedDevelopers — 랜딩의 "추천 개발자". 카드 3장을 나란히 놓습니다.
 *
 * "상담 시작하기"는 프로필로 보냅니다. 무료 상담은 프로필 하단 "무료로 물어보기"에서
 * 열리고, 그 앞에는 로그인이 필요하기 때문입니다.
 */
export function FeaturedDevelopers({ developers }: Readonly<FeaturedDevelopersProps>) {
  return (
    <section aria-labelledby="featured-heading" className="py-12">
      <div className="flex items-baseline justify-between gap-4">
        <h2 id="featured-heading" className="text-lg font-bold text-fg">
          추천 개발자
        </h2>
        <Link
          href={ROUTES.developers}
          className="rounded-sm text-[13px] font-medium text-fg-muted hover:text-fg hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          개발자 전체 보기
        </Link>
      </div>

      {developers.length === 0 ? (
        <p className="mt-5 rounded-xl bg-bg px-6 py-10 text-center text-sm text-fg-muted">
          아직 소개할 개발자가 없습니다.
        </p>
      ) : (
        <ul className="mt-5 grid grid-cols-1 gap-5 md:grid-cols-3">
          {developers.map((developer) => (
            <FeaturedDeveloperCard key={developer.developerId} developer={developer} />
          ))}
        </ul>
      )}
    </section>
  );
}

/** 요금표에서 작업 종류별 시작 금액을 꺼냅니다. 없으면 그 줄을 적지 않습니다. */
function priceOf(developer: FeaturedDeveloper, taskType: TaskType): number | undefined {
  return developer.prices.find((price) => price.taskType === taskType)?.price;
}

/** "DB 연결 5년" — 분야와 연차. 둘 다 없으면 비웁니다. 연차는 정수로 적습니다. */
function fieldLabel(developer: FeaturedDeveloper): string | null {
  const parts = [
    developer.field?.codeName,
    developer.yearsOfExperience === null ? undefined : `${Math.floor(developer.yearsOfExperience)}년`,
  ].filter(Boolean);
  return parts.length > 0 ? parts.join(" ") : null;
}

function FeaturedDeveloperCard({ developer }: Readonly<{ developer: FeaturedDeveloper }>) {
  const field = fieldLabel(developer);
  const consultPrice = priceOf(developer, "CONSULT");
  const developPrice = priceOf(developer, "DEVELOP");

  return (
    <Card as="li" padding="lg" className="flex flex-col">
      <div className="flex items-center gap-4">
        <Avatar size="lg" name={developer.name} />
        <div className="min-w-0">
          <p className="truncate text-base font-bold text-fg">{developer.name}</p>
          {field && <p className="mt-0.5 text-[13px] text-fg-subtle">{field}</p>}
        </div>
      </div>

      <p className="mt-5 text-sm text-fg-secondary">{developer.intro}</p>

      <div className="mt-4 flex flex-col gap-1.5">
        <RatingDots
          label="실력"
          value={developer.devRating ?? 0}
          score={developer.devRating?.toFixed(1) ?? "-"}
        />
        <RatingDots
          label="설명력"
          tone="primary"
          value={developer.explanatoryRating ?? 0}
          score={developer.explanatoryRating?.toFixed(1) ?? "-"}
        />
      </div>

      {(consultPrice !== undefined || developPrice !== undefined) && (
        <dl className="mt-4 flex flex-col gap-2 border-t border-line-subtle pt-4 text-[13px]">
          {consultPrice !== undefined && (
            <div className="flex items-center justify-between">
              <dt className="text-fg-subtle">컨설팅</dt>
              <dd>
                <Money value={consultPrice} from />
              </dd>
            </div>
          )}
          {developPrice !== undefined && (
            <div className="flex items-center justify-between">
              <dt className="text-fg-subtle">개발</dt>
              <dd>
                <Money value={developPrice} from />
              </dd>
            </div>
          )}
        </dl>
      )}

      <Link
        href={ROUTES.developer(developer.developerId)}
        className={buttonClass({ size: "lg", fullWidth: true, className: "mt-5" })}
      >
        상담 시작하기
      </Link>
    </Card>
  );
}
