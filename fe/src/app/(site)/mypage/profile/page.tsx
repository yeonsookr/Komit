import type { Metadata } from "next";
import Link from "next/link";
import { buttonClass } from "@/components/buttons";
import { Container } from "@/components/layouts";
import { PageHeader } from "@/components/common";
import { DeveloperProfileEditor } from "@/components/domains/developer";
import { EmptyState } from "@/components/feedback";
import { getCodes, getMyDeveloperProfile, getMyPage } from "@/lib/api/services";
import { requireLogin } from "@/lib/auth/guard";
import { ROUTES } from "@/lib/routes";
import { ApiError } from "@/types/api";

export const metadata: Metadata = { title: "개발자 내 프로필 | Komit", robots: { index: false } };

const CREATE_PARAM = "create";

async function getOptionalProfile() {
  try {
    return await getMyDeveloperProfile();
  } catch (error) {
    if (
      error instanceof ApiError
      && error.status === 403
      && error.code === "NO_DEVELOPER_PROFILE"
    ) {
      return null;
    }
    throw error;
  }
}

export default async function MyDeveloperProfilePage({
  searchParams,
}: Readonly<{
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}>) {
  const params = await searchParams;
  const createValue = params[CREATE_PARAM];
  const createRequested = (Array.isArray(createValue) ? createValue[0] : createValue) === "1";
  const profile = await requireLogin(getOptionalProfile);

  if (profile === null && !createRequested) {
    return (
      <Container className="max-w-4xl py-8 sm:py-10">
        <EmptyState
          className="py-16"
          title="개발자 프로필이 없습니다."
          description={
            <>
              지금 개발자 프로필을 등록하면 의뢰를 받을 수 있습니다.
              <br />
              Komit과 함께해보세요.
            </>
          }
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <Link
                href={`${ROUTES.mypageProfile}?${CREATE_PARAM}=1`}
                className={buttonClass({ size: "lg" })}
              >
                프로필 만들기
              </Link>
              <Link
                href={ROUTES.mypage}
                className={buttonClass({ variant: "outline", size: "lg" })}
              >
                뒤로가기
              </Link>
            </div>
          }
        />
      </Container>
    );
  }

  const [mypage, fields, technologies] = await requireLogin(() => Promise.all([
    getMyPage(),
    getCodes("DEV_FIELD"),
    getCodes("TECH_CATEGORY"),
  ]));

  return (
    <Container className="max-w-4xl py-8 sm:py-10">
      <PageHeader
        title={profile ? "프로필" : "개발자 프로필 만들기"}
        description="의뢰인에게 보일 소개와 경력, 작업 범위를 관리합니다."
        meta={profile
          ? `마지막 저장 ${new Date(profile.updatedAt).toLocaleDateString("ko-KR")}`
          : undefined}
      />
      <DeveloperProfileEditor
        profile={profile}
        account={mypage.account}
        fields={fields}
        technologies={technologies}
      />
    </Container>
  );
}
