import type { Metadata } from "next";
import { Container } from "@/components/layouts";
import { PageHeader } from "@/components/common";
import { DeveloperProfileEditor } from "@/components/domains/developer";
import { getCodes, getMyDeveloperProfile, getMyPage } from "@/lib/api/services";
import { requireLogin } from "@/lib/auth/guard";
import { ApiError } from "@/types/api";

export const metadata: Metadata = { title: "개발자 내 프로필 | Komit", robots: { index: false } };

async function getOptionalProfile() {
  try { return await getMyDeveloperProfile(); }
  catch (error) {
    if (error instanceof ApiError && error.code === "NO_DEVELOPER_PROFILE") return null;
    throw error;
  }
}

export default async function MyDeveloperProfilePage() {
  const [profile, mypage, fields, technologies] = await requireLogin(() => Promise.all([
    getOptionalProfile(), getMyPage(), getCodes("DEV_FIELD"), getCodes("TECH_CATEGORY"),
  ]));
  return <Container className="max-w-4xl py-8 sm:py-10"><PageHeader title={profile ? "프로필" : "개발자 프로필 만들기"} description="의뢰인에게 보일 소개와 경력, 작업 범위를 관리합니다." meta={profile ? `마지막 저장 ${new Date(profile.updatedAt).toLocaleDateString("ko-KR")}` : undefined} /><DeveloperProfileEditor profile={profile} account={mypage.account} fields={fields} technologies={technologies} /></Container>;
}
