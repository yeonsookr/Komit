import type { Metadata } from "next";
import { CheckupStart } from "@/components/domains/checkup";
import { getUnfinishedSurveys } from "@/lib/api/services";
import { ApiError } from "@/types/api";

export const metadata: Metadata = {
  title: "사전점검 | Komit",
  description:
    "만든 서비스가 지금 어떤 상태인지 3분 만에 점검합니다. 무엇이 막혀 있는지 문장으로 정리해 드립니다.",
};

/**
 * 설문1. 진단이 아직 없어 문항을 받지 않고, 프로젝트 이름과 설명만 받습니다.
 *
 * 끝내지 않은 설문 목록은 여기서 읽어 내립니다. 로그인하지 않았으면 목록이
 * 없을 뿐이므로 빈 배열로 두고 화면은 그대로 그립니다 — 설문 시작은 누구나
 * 볼 수 있어야 합니다.
 */
export default async function CheckupPage() {
  const unfinished = await getUnfinishedSurveys().catch((error: unknown) => {
    if (error instanceof ApiError) return [];
    throw error;
  });

  return <CheckupStart unfinished={unfinished} />;
}
