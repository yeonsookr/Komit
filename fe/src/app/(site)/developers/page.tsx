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
import { parseSearchParams, toQueryString } from "@/lib/developerSearch";
import { ROUTES } from "@/lib/routes";

export const metadata: Metadata = {
  title: "개발자 찾기 | Komit",
  description:
    "겪고 있는 문제를 적으면 그 문제를 다뤄 본 개발자를 찾아 드립니다. 분야와 정렬로 좁혀 보세요.",
};

/** 분야 선택지의 공통 코드 분류. */
const DEV_FIELD = "DEV_FIELD";

export default async function DevelopersPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = parseSearchParams(await searchParams);

  // 목록과 분야 선택지를 함께 읽습니다. 둘 다 로그인 없이 부를 수 있습니다.
  const [result, fields] = await Promise.all([findDevelopers(params), getCodes(DEV_FIELD)]);

  const shown = result.page * result.size;
  const rest = Math.max(0, result.totalElements - shown);

  return (
    <Container className="py-8 sm:py-10">
      <PageHeader title="개발자 찾기" />

      <div className="mt-6">
        <DeveloperSearchForm params={params} fields={fields} />
      </div>

      <div className="mt-8 flex flex-wrap items-center justify-between gap-3">
        <p className="text-[13px] text-fg-subtle">
          <span className="font-mono tabular-nums">{result.totalDevelopers}</span>명 중{" "}
          <span className="font-mono tabular-nums">{result.totalElements}</span>명
        </p>
        <DeveloperSortSelect params={params} />
      </div>

      {result.content.length === 0 ? (
        <EmptyState
          className="mt-6"
          title="조건에 맞는 개발자가 없습니다"
          description="분야를 줄이거나 검색어를 바꿔 보세요."
        />
      ) : (
        <>
          <p className="mt-2 text-lg font-bold text-fg">
            <span className="font-mono tabular-nums">{result.totalElements}</span>명
          </p>

          <ul className="mt-2">
            {result.content.map((developer, index) => (
              <li key={developer.developerId}>
                {index > 0 && <Divider />}
                <DeveloperCard developer={developer} />
              </li>
            ))}
          </ul>
        </>
      )}

      {/* 다음 장이 있으면 남은 사람 수를 적어 이어 보게 합니다. */}
      {result.page < result.totalPages && (
        <div className="mt-6 flex justify-center">
          <Link
            href={`${ROUTES.developers}?${toQueryString({ ...params, page: result.page + 1 })}`}
            className="inline-flex h-10 items-center rounded-md border border-line px-5 text-sm font-semibold text-fg hover:bg-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <span className="font-mono tabular-nums">{rest}</span>명 더 보기
          </Link>
        </div>
      )}
    </Container>
  );
}
