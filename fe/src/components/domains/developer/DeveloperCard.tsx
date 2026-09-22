import Link from "next/link";
import { Avatar, Badge, RatingDots, Tag } from "@/components/common";
import { Money } from "@/components/common";
import { formatFieldAndYears } from "@/lib/developerProfile";
import { ROUTES } from "@/lib/routes";
import type { DeveloperSummary, TaskType } from "@/types/developer";

/** 요금표에서 작업 종류별 시작 금액을 꺼냅니다. 없으면 그 줄을 적지 않습니다. */
function priceOf(developer: DeveloperSummary, taskType: TaskType): number | undefined {
  return developer.prices.find((price) => price.taskType === taskType)?.price;
}

export interface DeveloperCardProps {
  developer: DeveloperSummary;
}

/**
 * DeveloperCard — 개발자 찾기 목록의 한 줄.
 *
 * 머리는 `field`와 `yearsOfExperience`(분야는 한 명에 하나), 아래 칩은
 * `techCategories`(여럿)입니다. 거르는 기준은 분야, 칩은 기술이라 서로 다릅니다.
 */
export function DeveloperCard({ developer }: Readonly<DeveloperCardProps>) {
  const consultPrice = priceOf(developer, "CONSULT");
  const developPrice = priceOf(developer, "DEVELOP");

  return (
    <article className="flex gap-4 py-6">
      <Avatar
        size="lg"
        name={developer.name}
        src={developer.profileImageUrl ?? undefined}
        className="mt-0.5 shrink-0"
      />

      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-2">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <Link
                href={ROUTES.developer(developer.developerId)}
                className="rounded-sm text-base font-bold text-fg hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                {developer.name}
              </Link>
            </div>
            <p className="mt-0.5 flex flex-wrap items-center gap-2 text-[13px] text-fg-muted">
              <span>{formatFieldAndYears(developer.field, developer.yearsOfExperience)}</span>
              {developer.isCareerVerified && (
                <Badge tone="success" plain dot size="sm">
                  증빙 확인
                </Badge>
              )}
            </p>
          </div>

          <dl className="shrink-0 text-[13px]">
            {consultPrice !== undefined && (
              <div className="flex items-center justify-end gap-3">
                <dt className="text-fg-subtle">컨설팅</dt>
                <dd>
                  <Money value={consultPrice} from />
                </dd>
              </div>
            )}
            {developPrice !== undefined && (
              <div className="mt-1 flex items-center justify-end gap-3">
                <dt className="text-fg-subtle">개발</dt>
                <dd>
                  <Money value={developPrice} from />
                </dd>
              </div>
            )}
          </dl>
        </div>

        <p className="mt-3 text-sm text-fg-secondary">{developer.intro}</p>

        <div className="mt-3 flex flex-wrap items-center gap-x-6 gap-y-2">
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

          {developer.techCategories.length > 0 && (
            <ul className="flex flex-wrap gap-1.5">
              {developer.techCategories.map((tech) => (
                <li key={tech.codeId}>
                  <Tag>{tech.codeName}</Tag>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </article>
  );
}
