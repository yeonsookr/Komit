import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Container } from "@/components/layouts";
import { InquiryRequestPicker } from "@/components/domains/developer";
import { getDeveloper, getMyPage } from "@/lib/api/services";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

export const metadata: Metadata = {
  title: "요청서 고르기 | Komit",
  description: "어느 요청서로 개발자에게 물어볼지 고릅니다.",
  robots: { index: false },
};

/** 경로의 개발자 식별자를 읽습니다. 숫자가 아니면 없는 개발자로 봅니다. */
function parseDeveloperId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const id = Number(value);
  return id > 0 ? id : null;
}

/** 개발자가 있는지와 내 프로젝트를 함께 읽습니다. */
async function load(developerId: number) {
  try {
    const [, mypage] = await Promise.all([getDeveloper(developerId), getMyPage()]);
    return mypage;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    if (error instanceof ApiError && error.status === 401) redirect(ROUTES.login);
    throw error;
  }
}

export default async function DeveloperInquiryPage({
  params,
}: {
  params: Promise<{ developerId: string }>;
}) {
  const { developerId } = await params;
  const id = parseDeveloperId(developerId);
  if (id === null) notFound();

  const mypage = await load(id);
  // 진단이 있어야 요청서가 있습니다. 진단 전 프로젝트는 고를 것이 없어 뺍니다.
  const projects = mypage.projects.filter((project) => project.latestDiagnosisId !== null);

  return (
    <Container className="py-8 sm:py-10">
      <h1 className="text-2xl font-bold text-fg">어떤 요청서로 물어볼까요?</h1>
      <p className="mt-2 text-sm text-fg-muted">
        고른 요청서가 상담방에 공유됩니다. 개발자가 방에 입장하면 볼 수 있어요.
      </p>

      <InquiryRequestPicker developerId={id} projects={projects} />
    </Container>
  );
}
