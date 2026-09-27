import type { Metadata } from "next";
import Link from "next/link";
import { Container } from "@/components/layouts";
import { Divider, PageHeader } from "@/components/common";
import { EmptyState } from "@/components/feedback";
import {
  DeveloperCard,
  DeveloperSearchForm,
  DeveloperSortSelect,
} from "@/components/domains/developer";
import { findDevelopers, getCodes } from "@/lib/api/services";
import { DEFAULT_SIZE, parseSearchParams, toQueryString } from "@/lib/developerSearch";
import { ROUTES } from "@/lib/routes";

export const metadata: Metadata = {
  title: "개발자 찾기 | Komit",
  description:
    "겪고 있는 문제를 적으면 그 문제를 다뤄 본 개발자를 찾아 드립니다. 분야와 정렬로 좁혀 보세요.",
};

/** 분야 선택지의 공통 코드 분류. */
const DEV_FIELD = "DEV_FIELD";
/** GET /api/developers 의 size 상한. */
const MAX_API_PAGE_SIZE = 100;

export default async function DevelopersPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = parseSearchParams(await searchParams);

  // URL 의 page 는 화면에 펼친 묶음 수입니다. API 에는 첫 사람부터 필요한 만큼 요청합니다.
  const visibleLimit = params.page * DEFAULT_SIZE;
  const apiSize = Math.min(visibleLimit, MAX_API_PAGE_SIZE);
  const [result, fields] = await Promise.all([
    findDevelopers({ ...params, page: 1, size: apiSize }),
    getCodes(DEV_FIELD),
  ]);
  const targetCount = Math.min(visibleLimit, result.totalElements);
  const extraPageCount = Math.max(0, Math.ceil(targetCount / apiSize) - 1);
  const extraPages = await Promise.all(
    Array.from({ length: extraPageCount }, (_, index) =>
      findDevelopers({ ...params, page: index + 2, size: apiSize }),
    ),
  );
  const developers = [...result.content, ...extraPages.flatMap((page) => page.content)].slice(
    0,
    targetCount,
  );
  const shown = developers.length;
  const rest = Math.max(0, result.totalElements - shown);

  return (
    <Container className="py-8 sm:py-10">
      <PageHeader title="개발자 찾기" />

      <div className="mt-6">
        <DeveloperSearchForm params={params} fields={fields} />
      </div>

      <div className="mt-8 flex justify-end">
        <DeveloperSortSelect params={params} />
      </div>

      {developers.length === 0 ? (
        <EmptyState
          className="mt-6"
          title="조건에 맞는 개발자가 없습니다"
          description="분야를 줄이거나 검색어를 바꿔 보세요."
        />
      ) : (
        <>
          <p className="mt-2 text-lg font-bold text-fg">
            <span className="font-mono tabular-nums">{shown}</span>명
          </p>

          <ul className="mt-2">
            {developers.map((developer, index) => (
              <li key={developer.developerId}>
                {index > 0 && <Divider />}
                <DeveloperCard developer={developer} />
              </li>
            ))}
          </ul>
        </>
      )}

      {rest > 0 && (
        <div className="mt-6 flex justify-center">
          <Link
            href={`${ROUTES.developers}?${toQueryString({ ...params, page: params.page + 1 })}`}
            scroll={false}
            className="inline-flex h-10 items-center rounded-md border border-line px-5 text-sm font-semibold text-fg hover:bg-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            더 보기
          </Link>
        </div>
      )}
    </Container>
  );
}
