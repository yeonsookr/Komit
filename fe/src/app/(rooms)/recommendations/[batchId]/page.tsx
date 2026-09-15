import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Container } from "@/components/layouts";
import { Breadcrumb, PageHeader } from "@/components/common";
import { RecommendationPicker } from "@/components/domains/recommendation";
import { getRecommendationBatch } from "@/lib/api/services";
import { ApiError } from "@/types/api";

/** 경로의 회차 식별자를 읽습니다. 숫자가 아니면 없는 회차로 봅니다. */
function parseBatchId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const id = Number(value);
  return id > 0 ? id : null;
}

/** 회차를 읽습니다. 없거나 남의 추천이면 404 화면으로 보냅니다. */
async function loadBatch(batchId: number) {
  try {
    return await getRecommendationBatch(batchId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ batchId: string }>;
}): Promise<Metadata> {
  const { batchId } = await params;
  const id = parseBatchId(batchId);
  if (id === null) return { title: "추천 결과를 찾을 수 없습니다 | Komit" };

  const batch = await loadBatch(id);

  return {
    title: `${batch.projectName} 개발자 추천 | Komit`,
    description: "추천된 개발자에게 같은 요청서를 한 번에 보낼 수 있습니다.",
  };
}

export default async function RecommendationPage({
  params,
}: {
  params: Promise<{ batchId: string }>;
}) {
  const { batchId } = await params;
  const id = parseBatchId(batchId);
  if (id === null) notFound();

  const batch = await loadBatch(id);

  return (
    <Container className="py-8 sm:py-10">
      {/*
        와이어프레임의 "요구사항 N건"은 이 응답에 담기지 않습니다.
        `diagnosisVersion` 은 요구사항 수가 아니라 근거가 된 진단 회차라
        대신 쓰지 않고, 몇 번째 추천인지만 적습니다.
      */}
      <PageHeader
        breadcrumb={
          <Breadcrumb items={[batch.projectName, `${batch.sequenceNo}번째 추천`]} />
        }
        title="누구에게 상담을 요청할까요?"
        description="여러 명에게 같은 요청서를 보낼 수 있습니다"
      />

      <section className="mt-8">
        <h2 className="text-sm font-bold text-fg">
          추천 개발자{" "}
          <span className="font-normal text-fg-subtle">
            <span className="font-mono tabular-nums">{batch.candidateCount}</span>명 중{" "}
            <span className="font-mono tabular-nums">{batch.items.length}</span>명
          </span>
        </h2>
        <RecommendationPicker batch={batch} />
      </section>
    </Container>
  );
}
